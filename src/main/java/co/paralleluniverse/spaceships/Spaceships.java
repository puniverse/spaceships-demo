/*
 * Copyright (C) 2013 Parallel Universe Software Co.
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package co.paralleluniverse.spaceships;

import co.paralleluniverse.common.monitoring.Metrics;
import co.paralleluniverse.db.api.DbExecutors;
import co.paralleluniverse.db.api.Sync;
import co.paralleluniverse.spacebase.AABB;
import co.paralleluniverse.spacebase.MutableAABB;
import co.paralleluniverse.spacebase.SpaceBase;
import co.paralleluniverse.spacebase.SpaceBaseBuilder;
import co.paralleluniverse.spacebase.SpatialToken;
import co.paralleluniverse.spaceships.render.GLPort;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class Spaceships {
    public static Spaceships spaceships;
    public static final int POSTPONE_GLPORT_UNTIL_SB_CYCLE_UNDER_X_MILLIS = 250;
    private static final int MAX_PORT_POSTPONE_MILLIS = 10000;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) throws Exception {
        System.out.println("COMPILER: " + System.getProperty("java.vm.name"));
        System.out.println("VERSION: " + System.getProperty("java.version"));
        System.out.println("OS: " + System.getProperty("os.name"));
        System.out.println("PROCESSORS: " + Runtime.getRuntime().availableProcessors());
        System.out.println();

        Properties props = new Properties();
        props.load(new InputStreamReader(ClassLoader.getSystemResourceAsStream("spaceships.properties")));

        System.out.println("Initializing...");
        spaceships = new Spaceships(props);


        System.out.println("Running...");
        spaceships.run();
    }
    //
    private static final int CLEANUP_THREADS = 2;
    public final SpaceBase<Spaceship> sb;
    public final boolean extrapolate;
    public final RandSpatial random;
    public final AABB bounds;
    public final double speedVariance;
    public final boolean parallel;
    public final boolean async;
    public final double range;
    private File metricsDir;
    private PrintStream configStream;
    private PrintStream timeStream;
    private final GLPort.Toolkit toolkit;
    private final int N;
    private final ExecutorService executor;
    private final ArrayList<Spaceship> ships = new ArrayList<>(10000);
    private long cycleStart;
    private GLPort port = null;

    public Spaceships(Properties props) throws Exception {
        this.parallel = Boolean.parseBoolean(props.getProperty("parallel", "false"));
        this.async = Boolean.parseBoolean(props.getProperty("async", "true"));
        double b = Double.parseDouble(props.getProperty("world-length", "20000"));
        this.bounds = AABB.create(-b / 2, b / 2, -b / 2 * 0.7, b / 2 * 0.7, -b / 2, b / 2);
        this.N = Integer.parseInt(props.getProperty("N", "10000"));
        this.speedVariance = Double.parseDouble(props.getProperty("speed-variance", "1"));
        this.range = Double.parseDouble(props.getProperty("radar-range", "10"));
        this.extrapolate = Boolean.parseBoolean(props.getProperty("extrapolate", "true"));

        if (props.getProperty("dir") != null) // collect performance metrics in csv files
            createMetricsFiles(props);

        println("World bounds: " + bounds);
        println("N: " + N);

        this.executor = (!parallel ? createThreadPool(props) : null);
        this.random = new RandSpatial();

        // create all spaceships
        for (int i = 0; i < N; i++)
            ships.add(new Spaceship(this));

        this.sb = initSpaceBase(props);
        this.toolkit = GLPort.Toolkit.valueOf(props.getProperty("ui-component", "NEWT").toUpperCase());

        println("UI Component: " + toolkit);
    }

    /**
     * create a thread-pool for running in concurrent mode
     */
    private ExecutorService createThreadPool(Properties props) throws NumberFormatException {
        final int numThreads = Integer.parseInt(props.getProperty("parallelism", "2")) - CLEANUP_THREADS;
        return new ThreadPoolExecutor(numThreads, numThreads, 0L, TimeUnit.MILLISECONDS, new SynchronousQueue<Runnable>(), new RejectedExecutionHandler() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
                try {
                    executor.getQueue().put(r);
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }) {
            @Override
            protected void afterExecute(Runnable r, Throwable t) {
                if (t != null)
                    t.printStackTrace();
            }
        };
    }

    private void createMetricsFiles(Properties props) throws FileNotFoundException {
        this.metricsDir = new File(System.getProperty("user.home") + "/" + props.getProperty("dir"));

        if (metricsDir.isDirectory()) {
            for (File file : metricsDir.listFiles())
                file.delete();
        }
        metricsDir.mkdir();

        final File configFile = new File(metricsDir, "config.txt");
        this.configStream = new PrintStream(new FileOutputStream(configFile), true);

        final File timeFile = new File(metricsDir, "times.csv");
        this.timeStream = new PrintStream(new FileOutputStream(timeFile), true);
    }

    /**
     * reads properties file and creates a SpaceBase instance with the requested properties.
     */
    private SpaceBase<Spaceship> initSpaceBase(Properties props) {
        final boolean optimistic = Boolean.parseBoolean(props.getProperty("optimistic", "true"));
        final int optimisticHeight = Integer.parseInt(props.getProperty("optimistic-height", "1"));
        final int optimisticRetryLimit = Integer.parseInt(props.getProperty("optimistic-retry-limit", "3"));
        final int parallelism = Integer.parseInt(props.getProperty("parallelism", "2"));
        final boolean compressed = Boolean.parseBoolean(props.getProperty("compressed", "false"));
        final boolean singlePrecision = Boolean.parseBoolean(props.getProperty("single-precision", "false"));
        final int nodeWidth = Integer.parseInt(props.getProperty("node-width", "10"));

        println("Parallel: " + parallel);
        println("Parallelism: " + parallelism);
        println("Optimistic: " + optimistic);
        println("Optimistic height: " + optimisticHeight);
        println("Optimistic retry limit: " + optimisticRetryLimit);
        println("Node width: " + nodeWidth);
        println("Compressed: " + compressed);
        println("Single precision: " + singlePrecision);
        println();

        SpaceBaseBuilder builder = new SpaceBaseBuilder();

        if (parallel)
            builder.setExecutor(DbExecutors.parallel(parallelism));
        else
            builder.setExecutor(DbExecutors.concurrent(CLEANUP_THREADS));

        builder.setQueueBackpressure(1000);

        if (optimistic)
            builder.setOptimisticLocking(optimisticHeight, optimisticRetryLimit);
        else
            builder.setPessimisticLocking();

        builder.setDimensions(2);

        builder.setSinglePrecision(singlePrecision).setCompressed(compressed);
        builder.setNodeWidth(nodeWidth);

        builder.setMonitoringType(SpaceBaseBuilder.MonitorType.JMX);
        if (metricsDir != null) {
            com.codahale.metrics.CsvReporter.forRegistry(Metrics.registry())
                    .convertRatesTo(TimeUnit.SECONDS)
                    .convertDurationsTo(TimeUnit.MILLISECONDS)
                    .build(metricsDir)
                    .start(1, TimeUnit.SECONDS);
        }

        final SpaceBase<Spaceship> space = builder.build("base1");
        return space;
    }

    /**
     * Main loop: loops over all spaceships and initiates each spaceship's actions. Simulates an IO thread receiving commands over the net.
     */
    private void run() throws Exception {
        // insert all spaceships into SB
        {
            System.out.println("Inserting " + N + " spaceships");
            long insrertStart = System.nanoTime();
            for (Spaceship s : ships) {
                insertSpaceship(s);
            }
            System.out.println("Inserted " + N + " things in " + millis(insrertStart));
        }
        long initTime = System.nanoTime();

        if (timeStream != null)
            timeStream.println("# time, millis, millis1, millis0");

        sb.setCurrentThreadAsynchronous(async);

        for (int k = 0;; k++) {
            cycleStart = System.nanoTime();

            for (final Spaceship s : ships) {
                final Runnable command = new Runnable() {
                    @Override
                    public void run() {
                        try {
                            s.join(); // wait for this spaceship's previous action (from the previous cycle) to complete
                            final Sync sync = s.run(Spaceships.this);
                            s.setSync(sync);
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e);
                        }
                    }
                };

                if (executor == null)
                    command.run();
                else
                    executor.submit(command);
            }

            float millis = millis(cycleStart);
            if (timeStream != null)
                timeStream.println(k + "," + millis);

            if (millis(cycleStart) < 10) // don't work too hard: if the cycle has taken less than 10 millis, wait a little.
                Thread.sleep(10 - (int) millis(cycleStart));

            millis = millis(cycleStart);

            if (port == null
                    & (millis < POSTPONE_GLPORT_UNTIL_SB_CYCLE_UNDER_X_MILLIS
                    | millis(initTime) > MAX_PORT_POSTPONE_MILLIS)) // wait for JIT to make everything run smoothly before opening port
                port = new GLPort(toolkit, N, Spaceships.this, bounds);

            System.out.println("CYCLE: " + millis + " millis " + (executor != null && executor instanceof ThreadPoolExecutor ? " executorQueue: " + ((ThreadPoolExecutor) executor).getQueue().size() : ""));
        }
    }

    public ArrayList<Spaceship> getShips() {
        return ships;
    }

    public void insertSpaceship(Spaceship s) throws InterruptedException {
        if (!ships.contains(s))
            ships.add(s);
        MutableAABB aabb = MutableAABB.create(2);
        s.getAABB(aabb);
        final SpatialToken token = sb.insert(s, aabb);
        token.join();
        s.setToken(token);
    }

    public void deleteSpaceship(Spaceship toRemove) {
        final Spaceship s = new Spaceship(this);
        ships.set(ships.indexOf(toRemove), s);
        sb.delete(toRemove.getToken());
        s.setToken(sb.insert(s, s.getAABB()));
    }

    public long getCycleStart() {
        return cycleStart;
    }

    long currentTime() {
        return System.currentTimeMillis();
    }

    private float millis(long nanoStart) {
        return (float) (System.nanoTime() - nanoStart) / 1000000;
    }

    private void println() {
        println("");
    }

    private void println(String str) {
        if (configStream != null)
            configStream.println(str);
        System.out.println(str);
    }
}
