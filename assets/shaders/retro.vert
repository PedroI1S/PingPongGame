// Pass-through vertex shader for a full-screen SpriteBatch quad.
// SpriteBatch supplies a_position, a_color, a_texCoord0 and u_projTrans.

attribute vec4 a_position;
attribute vec4 a_color;
attribute vec2 a_texCoord0;

uniform mat4 u_projTrans;

varying vec4 v_color;
varying vec2 v_texCoords;

void main() {
    v_color     = a_color;
    v_texCoords = a_texCoord0;
    gl_Position = u_projTrans * a_position;
}
