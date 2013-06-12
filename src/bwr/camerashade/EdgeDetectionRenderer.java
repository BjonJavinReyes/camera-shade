package bwr.camerashade;

import android.util.Log;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.locks.ReentrantLock;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import static android.opengl.GLES20.*;
import android.opengl.GLES11Ext;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;

public class EdgeDetectionRenderer extends GLSurfaceView implements GLSurfaceView.Renderer, OnFrameAvailableListener {
	private static String TAG = "CameraShade";
	
	private final int FLOAT_SIZE_BYTES = 4;
	
	private FloatBuffer vertexBuffer;
	private int vertexPositionAttribute;
	private int imageInputTexture;

	// A triangle that covers the entire viewport
	private final float[] vertex_array = {
			-2.0f, -1.0f, 
			 0.0f, 3.0f, 
			 3.0f, -1.0f, 
	};

	private SurfaceTexture surfaceTexture = null;
	private int shaderProgram;
	private int vertexShader;
	private int fragmentShader;
	private int displayWidth, displayHeight;
	private boolean newFrameAvailable = false;

	private Camera camera;
	private ReentrantLock lock = new ReentrantLock();

	private void checkGlError(String op) {
		int error;
		while ((error = glGetError()) != GL_NO_ERROR) {
			Log.e(TAG, op + ": glError " + error);
			Log.e(TAG, GLUtils.getEGLErrorString(error));

			throw new RuntimeException(op + ": glError " + error);
		}
	}

	public EdgeDetectionRenderer(Context context) {
		super(context);
		setEGLContextClientVersion(2);
		setRenderer(this);

		setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);

		vertexBuffer = ByteBuffer
				.allocateDirect(vertex_array.length * FLOAT_SIZE_BYTES)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		vertexBuffer.put(vertex_array).position(0);
	}

	private int loadShader(int shaderType, String source) {
		int shader = glCreateShader(shaderType);
		if (shader != 0) {
			glShaderSource(shader, source);
			glCompileShader(shader);
			int[] compiled = new int[1];
			glGetShaderiv(shader, GL_COMPILE_STATUS, compiled, 0);
			if (compiled[0] == 0) {
				Log.e(TAG, "Could not compile shader " + shaderType + ":");
				Log.e(TAG, glGetShaderInfoLog(shader));
				glDeleteShader(shader);
				shader = 0;
			}
		}
		return shader;
	}

	private int loadShaderAsset(int type, String asset) {
		StringBuffer sourceBuffer = new StringBuffer();

		byte[] buffer = new byte[1024];

		try {
			InputStream in = getContext().getAssets().open(asset);
			int len;
			while ((len = in.read(buffer)) > 0) {
				sourceBuffer.append(new String(buffer, 0, len));
			}
			in.close();

			String shaderSource = sourceBuffer.toString();

			return loadShader(type, shaderSource);

		} catch (IOException e) {
			e.printStackTrace();
		}

		return 0;
	}

	@Override
	public void onPause() {
		super.onPause();

		if (camera != null) {
			camera.stopPreview();
			camera.release();
			camera = null;
		}
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	@Override
	public void onDrawFrame(GL10 nil) {
		if (surfaceTexture == null) {
			glClear(GL_COLOR_BUFFER_BIT);
			return;
		}

		lock.lock();
		try {
			if (newFrameAvailable) {
				surfaceTexture.updateTexImage();
				newFrameAvailable = false;
			} else {
				glClear(GL_COLOR_BUFFER_BIT);
				return;
			}

			glUseProgram(shaderProgram);
			checkGlError("use program");

			int th = glGetUniformLocation(shaderProgram, "inputImage");
			glActiveTexture(GL_TEXTURE0);
			glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, imageInputTexture);
			glUniform1i(th, 0);
			
			int displayUniform = glGetUniformLocation(shaderProgram, "display");
			glUniform2f(displayUniform, displayWidth, displayHeight);
			
			vertexPositionAttribute = glGetAttribLocation(shaderProgram, "position");
			checkGlError("get position handle");

			vertexBuffer.position(0);
			glVertexAttribPointer(vertexPositionAttribute, 2, GL_FLOAT, false,
					FLOAT_SIZE_BYTES * 2, vertexBuffer);
			checkGlError("vertices");

			glEnableVertexAttribArray(vertexPositionAttribute);
			checkGlError("position handle");

			glDrawArrays(GL_TRIANGLES, 0, 3);
			checkGlError("draw arrays");
			glFlush();
		} finally {
			lock.unlock();
		}
	}

	private void initSurfaceTexture() {
		int[] textures = new int[1];
		glGenTextures(1, textures, 0);
		glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0]);
		glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_S, GL_REPEAT);
		glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_WRAP_T, GL_REPEAT);
		glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
		glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

		imageInputTexture = textures[0];
		
		surfaceTexture = new SurfaceTexture(imageInputTexture);
		surfaceTexture.setOnFrameAvailableListener(this);
	}

	public void setupStuff() {
		initSurfaceTexture();

		vertexShader = loadShaderAsset(GL_VERTEX_SHADER, "vertex.glsl");
		fragmentShader = loadShaderAsset(GL_FRAGMENT_SHADER, "fragment.glsl");
		
		shaderProgram = glCreateProgram();
		if (shaderProgram != 0) {
			glAttachShader(shaderProgram, vertexShader);
			checkGlError("glAttachShader");
			glAttachShader(shaderProgram, fragmentShader);
			checkGlError("glAttachShader");
			glLinkProgram(shaderProgram);
			int[] linkStatus = new int[1];
			glGetProgramiv(shaderProgram, GL_LINK_STATUS, linkStatus, 0);
			if (linkStatus[0] != GL_TRUE) {
				Log.e(TAG, "Could not link program: ");
				Log.e(TAG, glGetProgramInfoLog(shaderProgram));
				glDeleteProgram(shaderProgram);
				shaderProgram = 0;
			}
		}

	}

	@Override
	public void onSurfaceChanged(GL10 nil, int width, int height) {
		glViewport(0, 0, width, height);
		
		displayWidth = width;
		displayHeight = height;
		
		camera = Camera.open();
		try {
			camera.setPreviewTexture(surfaceTexture);
		} catch (IOException e) {
			e.printStackTrace(); 
		}

		Camera.Parameters param = camera.getParameters();
				
		camera.setParameters(param);
		
		camera.startPreview();
	}

	@Override
	public void onSurfaceCreated(GL10 nil, EGLConfig glConfig) {
		glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
		setupStuff();
	}

	@Override
	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
		lock.lock();
		try {
			newFrameAvailable = true;
		} finally {
			lock.unlock();
			requestRender();
		}
	}

}
