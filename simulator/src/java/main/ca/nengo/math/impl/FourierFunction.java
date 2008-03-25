/*
 * Created on 6-Jun-2006
 */
package ca.nengo.math.impl;

import java.util.Random;

import ca.nengo.math.Function;

/**
 * A Function that is composed of a finite number of sinusoids.
 * 
 * @author Bryan Tripp
 */
public class FourierFunction implements Function {

	private static final long serialVersionUID = 1L;
	
	private float[][] myFrequencies;
	private float[] myAmplitudes;
	private float[][] myPhases;
	
	/**
	 * Creates a 1-dimensional function composed of explicitly defined sinusoids. 
	 * 
	 * @param frequencies Explicit list of frequencies of sinusoidal components of the 
	 * 		function (Hz)
	 * @param amplitudes The amplitude of each component 
	 * @param phases The phase lead of each component (from -.5 to .5)
	 */
	public FourierFunction(float[] frequencies, float[] amplitudes, float[] phases) {
		set(new float[][]{frequencies}, amplitudes, new float[][]{phases});
	}
	
	/**
	 * Creates an n-dimensional function composed of explicitly defined sinusoids. 
	 * 
	 * @param frequencies Lists of frequencies (length n; ith members define frequencies of ith component along each dimension)  
	 * @param amplitudes The amplitude of each component
	 * @param phases Lists of phases (length n; ith members define phases of ith component along each dimension)
	 */
	public FourierFunction(float[][] frequencies, float[] amplitudes, float[][] phases) {
		set(frequencies, amplitudes, phases);
	}
	
	/**
	 * Creates a 1-dimensional band-limited pink noise function with specified parameters. 
	 *  
	 * @param fundamental The fundamental frequency (Hz)
	 * @param cutoff The high-frequency limit (Hz)
	 * @param rms The root-mean-squared function amplitude
	 */
	public FourierFunction(float fundamental, float cutoff, float rms, long seed) {
		int n = (int) Math.floor(cutoff / fundamental);
		
		float[][] frequencies = new float[][]{new float[n]};
		float[] amplitudes = new float[n];
		float[][] phases = new float[][]{new float[n]};
		Random random = new Random(seed); 
		
		for (int i = 0; i < n; i++) {
			frequencies[0][i] = fundamental * (i+1);
			amplitudes[i] = (float) random.nextFloat() * fundamental / frequencies[0][i]; //decreasing amplitude = pink noise
			phases[0][i] = -.5f + 2f * (float) random.nextFloat();
		}
		
		//find amplitude over one period and rescale to specified amplitude 
		int samplePoints = 500;
		float dx = (1f / fundamental) / samplePoints;
		double sumSquared = 0;
		for (int i = 0; i < samplePoints; i++) {
			float val = getValue(new float[]{i*dx}, frequencies, amplitudes, phases);
			sumSquared += val * val;
		}
		double unscaledRMS = Math.sqrt(sumSquared / samplePoints);
		
		for (int i = 0; i < n; i++) {
			amplitudes[i] = amplitudes[i] * rms / (float) unscaledRMS;
		}
		
		set(frequencies, amplitudes, phases);
	}
	
	private void set(float[][] frequencies, float[] amplitudes, float[][] phases) {
		if (frequencies.length != phases.length) {
			throw new IllegalArgumentException("Lists of frequencies and phases must have same dimension");
		}
		
		if (frequencies[0].length != amplitudes.length || phases[0].length != amplitudes.length) {
			throw new IllegalArgumentException("Frequencies, amplitudes, and phases must have same length in each dimension");
		}
		
		myFrequencies = frequencies;
		myAmplitudes = amplitudes;
		myPhases = phases;
	}
	
	/**
	 * @see ca.nengo.math.Function#getDimension()
	 */
	public int getDimension() {
		return myFrequencies.length;
	}
	
	/**
	 * @return Number of frequency components  
	 */
	public int getComponents() {
		return myFrequencies[0].length;
	}
	
	/**
	 * @return Lists of frequencies (length n; ith members define frequencies of ith component along each dimension)
	 */
	public float[][] getFrequencies() {
		return myFrequencies;
	}
	
	/**
	 * @param frequencies Lists of frequencies (length n; ith members define frequencies of ith component along each dimension)
	 */
	public void setFrequencies(float[][] frequencies) {
		set(frequencies, getAmplitudes(), getPhases());
	}
	
	/**
	 * @return The amplitude of each component
	 */
	public float[] getAmplitudes() {
		return myAmplitudes;
	}
	
	/**
	 * @param amplitudes The amplitude of each component
	 */
	public void setAmplitudes(float[] amplitudes) {
		set(getFrequencies(), amplitudes, getPhases());
	}
	
	/**
	 * @return Lists of phases (length n; ith members define phases of ith component along each dimension)
	 */
	public float[][] getPhases() {
		return myPhases;
	}

	/**
	 * @param phases Lists of phases (length n; ith members define phases of ith component along each dimension)
	 */
	public void setPhases(float[][] phases) {
		set(getFrequencies(), getAmplitudes(), phases);
	}
	
	/**
	 * @see ca.nengo.math.Function#map(float[])
	 */
	public float map(float[] from) {
		return getValue(from, myFrequencies, myAmplitudes, myPhases);
	}
	
	/**
	 * @see ca.nengo.math.Function#multiMap(float[][])
	 */
	public float[] multiMap(float[][] from) {
		float[] result = new float[from.length];
		
		for (int i = 0; i < from.length; i++) {
			result[i] = getValue(from[i], myFrequencies, myAmplitudes, myPhases);
		}
		
		return result;
	}

	private static float getValue(float[] x, float[][] f, float[] a, float[][] p) {
		float result = 0f;
		
		for (int i = 0; i < f[0].length; i++) {
			float component = 1;
			for (int j = 0; j < x.length; j++) {
				component = component * (float) Math.sin(2d * Math.PI * (f[j][i] * x[j] + p[j][i]));
			}
			result += a[i] * component;
		}
		
		return result;
	}
	
	@Override
	public Function clone() throws CloneNotSupportedException {
		return new FourierFunction(myFrequencies.clone(), myAmplitudes.clone(), myPhases.clone());		
	}
	
}