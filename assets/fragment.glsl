#extension GL_OES_EGL_image_external : require

precision mediump float;

uniform samplerExternalOES inputImage;

const float onehalf = 1.0/2.0;

uniform vec2 display; // = vec2(displayWidth, displayheight)

float tex(vec2 loc) {	
	vec2 idx = loc * (vec2(1.0,1.0) / display);
	vec4 v = texture2D(inputImage, idx);
	return (v.r + v.g + v.b)/3.0;
}

float edgedetect(vec2 pos) {
	float lx = -onehalf * tex(pos + vec2(-1.0, 0.0)) + onehalf * tex(pos + vec2( 1.0, 0.0));

	float ly = -onehalf * tex(pos + vec2( 0.0,-1.0)) + onehalf * tex(pos + vec2( 0.0, 1.0));

	return sqrt(pow(lx, 2.0) + pow(ly, 2.0));
}

void main() {
	vec2 cur = vec2( gl_FragCoord.x , display.y - gl_FragCoord.y );
		
	float mag = edgedetect(cur);
	
	gl_FragColor.rgb = vec3(pow(min(mag+0.7,1.0),5.0));
}
