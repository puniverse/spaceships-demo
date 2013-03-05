#version 150
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
 
layout(points) in;
layout (triangle_strip, max_vertices=10) out;
in float pass_Color[1];
in float heading[1];
in float shootLength[1];
uniform mat4 in_Matrix;
out float explosionTime;
out vec2 vTexCoord;
out float light;
vec4 lightSource = vec4(0.707,0,0.707,0);
float size = 16;  

struct geomVertex {
    vec4 pos;
    vec2 texPos;
    vec4 light;
};

geomVertex gv[10] = geomVertex[10](
// spaceship body
    geomVertex(vec4(-size,-size,0,0),vec2(0,1),vec4(-1,0,0,0)),
    geomVertex(vec4(-size,+size,0,0),vec2(0,0),vec4(-1,0,0,0)),
    geomVertex(vec4(0,-size,0,0),vec2(0.5,1),vec4(0,0,1,0)),
    geomVertex(vec4(0,+size,0,0),vec2(0.5,0),vec4(0,0,1,0)),
    geomVertex(vec4(+size,-size,0,0),vec2(1,1),vec4(1,0,0,0)),
    geomVertex(vec4(+size,+size,0,0),vec2(1,0),vec4(1,0,0,0)),
// the shoot beam
    geomVertex(vec4(-size/10,+size,0,0),vec2(0.5,0.8),vec4(1,0,0,0)),
    geomVertex(vec4(-size/10,+shootLength[0],0,0),vec2(0.5,0.9),vec4(1,0,0,0)),
    geomVertex(vec4(+size/10,+size,0,0),vec2(0.5,0.8),vec4(1,0,0,0)),
    geomVertex(vec4(+size/10,+shootLength[0],0,0),vec2(0.5,0.9),vec4(1,0,0,0))
);
  
 void main()
{
  explosionTime = pass_Color[0];
  mat4 RotationMatrix = mat4( cos( heading[0] ), -sin( heading[0] ), 0.0, 0.0,
			    sin( heading[0] ),  cos( heading[0] ), 0.0, 0.0,
			             0.0,           0.0, 1.0, 0.0,
				     0.0,           0.0, 0.0, 1.0 );
  for(int i = 0; i < gl_in.length(); i++)
  {
    for (int j=0; j< 6; j++) { // first 6 vertices is the spaceship body
        gl_Position = in_Matrix * (gl_in[i].gl_Position + RotationMatrix * gv[j].pos);
        vTexCoord = gv[j].texPos;
        light = dot(RotationMatrix * gv[j].light,lightSource);
        EmitVertex();
    }
    EndPrimitive();    
    if (shootLength[0]>0) {
        light = 1.0;
        for (int j=6; j< 10; j++) { // next 4 vertices is the shoot beam 
            gl_Position = in_Matrix * (gl_in[i].gl_Position + RotationMatrix *gv[j].pos);
            vTexCoord = gv[j].texPos;
            EmitVertex();
        }
        EndPrimitive();
    }
  }    
}