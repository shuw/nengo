/*
 * Created on 7-Jun-2006
 */
package ca.nengo.sim.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import ca.nengo.model.Ensemble;
import ca.nengo.model.InstantaneousOutput;
import ca.nengo.model.Network;
import ca.nengo.model.Node;
import ca.nengo.model.Probeable;
import ca.nengo.model.Projection;
import ca.nengo.model.SimulationException;
import ca.nengo.sim.Simulator;
import ca.nengo.sim.SimulatorEvent;
import ca.nengo.sim.SimulatorListener;
import ca.nengo.util.Probe;
import ca.nengo.util.VisiblyMutable;
import ca.nengo.util.VisiblyMutableUtils;
import ca.nengo.util.impl.ProbeImpl;

/**
 * A Simulator that runs locally (ie in the Java Virtual Machine in which it is
 * called). TODO: test
 * 
 * @author Bryan Tripp
 */
public class LocalSimulator implements Simulator, java.io.Serializable {
	private static final long serialVersionUID = 1L;
	
	private Projection[] myProjections;
	private Node[] myNodes;
	private Map<String, Node> myNodeMap;
	private List<Probe> myProbes;
	private transient List<VisiblyMutable.Listener> myChangeListeners;	

	/**
	 * Collection of Simulator
	 */
	private Collection<SimulatorListener> mySimulatorListeners;

	public LocalSimulator() {
		mySimulatorListeners = new ArrayList<SimulatorListener>(1);
		myChangeListeners = new ArrayList<Listener>(1);
	}

	/**
	 * @see ca.nengo.sim.Simulator#initialize(ca.nengo.model.Network)
	 */
	public synchronized void initialize(Network network) {
		myNodes = network.getNodes();

		myNodeMap = new HashMap<String, Node>(myNodes.length * 2);
		for (int i = 0; i < myNodes.length; i++) {
			myNodeMap.put(myNodes[i].getName(), myNodes[i]);
		}

		myProjections = network.getProjections();
		if (myProbes == null)
			myProbes = new ArrayList<Probe>(20);
	}

	/**
	 * @see ca.nengo.sim.Simulator#run(float, float, float)
	 */
	public synchronized void run(float startTime, float endTime, float stepSize)
			throws SimulationException {

		Iterator<Probe> it = myProbes.iterator();
		while (it.hasNext()) {
			it.next().reset();
		}
		
		fireSimulatorEvent(new SimulatorEvent(0, SimulatorEvent.Type.STARTED));
		// for (int i = 0; i < myNodes.length; i++) {
		// myNodes[i].setMode(mode);
		// }

		// //make each node produce its initial output
		// for (int i = 0; i < myNodes.length; i++) {
		// myNodes[i].run(startTime, startTime);
		// }
		//		
		double time = startTime;
		double thisStepSize = stepSize;

		int c = 0;
		while (time < endTime) { 
			if (c++ % 100 == 99)
				System.out.println("Step " + c + " " + Math.min(endTime, time + thisStepSize)); 
			
			if (time + 1.5*thisStepSize > endTime) { //fudge step size to hit end exactly
				thisStepSize = endTime - time;
			}
			step((float) time, (float) (time+thisStepSize));

			float currentProgress = ((float) time - startTime) / (endTime - startTime);
			fireSimulatorEvent(new SimulatorEvent(currentProgress,
					SimulatorEvent.Type.STEP_TAKEN));

			time += thisStepSize;
		}
		fireSimulatorEvent(new SimulatorEvent(1f, SimulatorEvent.Type.FINISHED));
	}

	private void step(float startTime, float endTime)
			throws SimulationException {

		for (int i = 0; i < myProjections.length; i++) {
			InstantaneousOutput values = myProjections[i].getOrigin()
					.getValues();
			myProjections[i].getTermination().setValues(values);
		}

		for (int i = 0; i < myNodes.length; i++) {
			myNodes[i].run(startTime, endTime);
		}

		Iterator<Probe> it = myProbes.iterator();
		while (it.hasNext()) {
			it.next().collect(endTime);
		}
	}

	/**
	 * @see ca.nengo.sim.Simulator#resetNetwork(boolean)
	 */
	public synchronized void resetNetwork(boolean randomize) {
		for (int i = 0; i < myNodes.length; i++) {
			myNodes[i].reset(randomize);
		}
	}

	/**
	 * @see ca.nengo.sim.Simulator#addProbe(java.lang.String, java.lang.String,
	 *      boolean)
	 */
	public Probe addProbe(String nodeName, String state, boolean record)
			throws SimulationException {
		Probeable p = getNode(nodeName);
		return addProbe(null, p, state, record);
	}

	/**
	 * @see ca.nengo.sim.Simulator#addProbe(java.lang.String, int,
	 *      java.lang.String, boolean)
	 */
	public Probe addProbe(String ensembleName, int neuronIndex, String state,
			boolean record) throws SimulationException {
		Probeable p = getNeuron(ensembleName, neuronIndex);
		return addProbe(ensembleName, p, state, record);
	}

	/**
	 * @see ca.nengo.sim.Simulator#addProbe(java.lang.String, int,
	 *      java.lang.String, boolean)
	 */
	public Probe addProbe(String ensembleName, Probeable target, String state,
			boolean record) throws SimulationException {

		/*
		 * Check that no duplicate probes are created
		 */
		for (Probe probe : myProbes) {			
			if (probe.getTarget() == target) {
				if (probe.getStateName().compareTo(state) == 0) {
					throw new SimulationException("A probe already exists on this target & state");
				}	
			}
		}
		
		Probe result = new ProbeImpl();
		result.connect(ensembleName, target, state, record);

		myProbes.add(result);

		fireVisibleChangeEvent();
		return result;
	}

	/**
	 * @see ca.nengo.sim.Simulator#removeProbe(ca.nengo.util.Probe)
	 */
	public void removeProbe(Probe probe) throws SimulationException {
		if (!myProbes.remove(probe)) {
			throw new SimulationException("Probe could not be removed");
		}
		fireVisibleChangeEvent();
	}

	private Probeable getNode(String nodeName) throws SimulationException {
		Node result = (Node) myNodeMap.get(nodeName);

		if (result == null) {
			throw new SimulationException("The named Node does not exist");
		}

		if (!(result instanceof Probeable)) {
			throw new SimulationException("The named Node is not Probeable");
		}

		return (Probeable) result;
	}

	private Probeable getNeuron(String nodeName, int index)
			throws SimulationException {
		Node ensemble = (Node) myNodeMap.get(nodeName);

		if (ensemble == null) {
			throw new SimulationException("The named Ensemble does not exist");
		}

		if (!(ensemble instanceof Ensemble)) {
			throw new SimulationException("The named Node is not an Ensemble");
		}

		Node[] nodes = ((Ensemble) ensemble).getNodes();
		if (index < 0 || index >= nodes.length) {
			throw new SimulationException("The Node index " + index
					+ " is out of range for Ensemble size " + nodes.length);
		}

		if (!(nodes[index] instanceof Probeable)) {
			throw new SimulationException("The specified Node is not Probeable");
		}

		return (Probeable) nodes[index];
	}

	/**
	 * @see ca.nengo.sim.Simulator#getProbes()
	 */
	public Probe[] getProbes() {
		return myProbes.toArray(new Probe[0]);
	}

	/**
	 * @see ca.nengo.sim.Simulator#addSimulatorListener(ca.nengo.sim.SimulatorListener)
	 */
	public void addSimulatorListener(SimulatorListener listener) {
		if (mySimulatorListeners.contains(listener)) {
			System.out
					.println("Trying to add simulator listener that already exists");
		} else {
			mySimulatorListeners.add(listener);
		}
	}

	/**
	 * @param event
	 */
	protected void fireSimulatorEvent(SimulatorEvent event) {
		for (SimulatorListener listener : mySimulatorListeners) {
			listener.processEvent(event);
		}
	}

	/**
	 * @see ca.nengo.sim.Simulator#removeSimulatorListener(ca.nengo.sim.SimulatorListener)
	 */
	public void removeSimulatorListener(SimulatorListener listener) {
		mySimulatorListeners.remove(listener);
	}

	/**
	 * @see ca.nengo.util.VisiblyMutable#addChangeListener(ca.nengo.util.VisiblyMutable.Listener)
	 */
	public void addChangeListener(Listener listener) {
		if (myChangeListeners == null) {
			myChangeListeners = new ArrayList<Listener>(1);
		}
		myChangeListeners.add(listener);
	}

	/**
	 * @see ca.nengo.util.VisiblyMutable#removeChangeListener(ca.nengo.util.VisiblyMutable.Listener)
	 */
	public void removeChangeListener(Listener listener) {
		if (myChangeListeners != null) myChangeListeners.remove(listener);
	}
	
	private void fireVisibleChangeEvent() {
		VisiblyMutableUtils.changed(this, myChangeListeners);
	}

	@Override
	public Simulator clone() throws CloneNotSupportedException {
		return new LocalSimulator();
	}
	
}