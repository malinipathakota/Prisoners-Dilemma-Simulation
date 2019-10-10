package agents;

import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.field.grid.SparseGrid2D;
import sim.util.Bag;
import simulation.PDWASim;

public class Observer implements Steppable {
	
	private int nNaiveC;
	private int nNaiveD;
	private int nWalkawayC;
	private int nWalkawayD;
	private int nTFTStationary;
	private int nTFTMobile;
	private int nPAVLOVStationary;
	private int nPAVLOVMobile;
	private int nRealisticTFT;
	private int nRetreat;
	
	private PDWASim sim;
	private SparseGrid2D space;
	private Stoppable stopper;
	
	public Observer(PDWASim sim) {
		this.sim = sim;
		space = sim.acquireSpace();
		printHeaders();
	}

	@Override
	public void step(SimState state) {
		countAndReset();
		printDataline();
		return;
	}
	
	/**
	 * Provide the object that allows this agent to be removed from the schedule. This is returned by the schedule when placing an object on the schedule for repeating time steps.
	 * @param stopper stoppable object returned by schedule
	 */
	public void attachStopper(Stoppable stopper) {
		this.stopper = stopper;
		return;
	}
	
	/**
	 * Reset the counters then count the number of agents of each type into the counters; also resets all agents so they can be played in the next time step.
	 */
	private void countAndReset() {
		nNaiveC = nNaiveD = nWalkawayC = nWalkawayD = nTFTStationary = nTFTMobile = nPAVLOVStationary = nPAVLOVMobile = nRealisticTFT = nRetreat = 0;	// reset counters
		Bag b = space.allObjects;
		for (int i = 0; i < b.numObjs; i++) {
			Agent a = (Agent)b.objs[i];
			switch (a.getStrategy()) {
				case NAIVE_C:
					nNaiveC++;
					break;
				case NAIVE_D:
					nNaiveD++;
					break;
				case PAVLOV_MOBILE:
					nPAVLOVMobile++;
					break;
				case PAVLOV_STATIONARY:
					nPAVLOVStationary++;
					break;
				case TFT_MOBILE:
					nTFTMobile++;
					break;
				case TFT_STATIONARY:
					nTFTStationary++;
					break;
				case WALKAWAY_C:
					nWalkawayC++;
					break;
				case WALKAWAY_D:
					nWalkawayD++;
					break;
				case REALISTIC_TFT:
					nRealisticTFT++;
					break;
				case RETREAT:
					nRetreat++;
					break;
				default:
					throw new RuntimeException("Found an agent with an uncountable strategy: " + a.getStrategy());
			}
			a.reset();
		}
		if (b.numObjs == 0) {	// if there are no more agents, end after this step
			stopper.stop();
		}
		return;
	}
	
	/**
	 * Print current line of data from the counters.
	 */
	private void printDataline() {
		long steps = sim.schedule.getSteps();
		System.out.println(steps + "\t" + nNaiveC + "\t" + nNaiveD + "\t" + nWalkawayC + "\t" + nWalkawayD + "\t" + nTFTStationary + "\t" + nTFTMobile + "\t" + nPAVLOVStationary + "\t" + nPAVLOVMobile + "\t" + nRealisticTFT + "\t" + nRetreat);
		return;
	}
	
	/**
	 * Print the data file headers at the beginning of the simulation.
	 */
	private void printHeaders() {
		System.out.println("step\tnNaiveC\tnNaiveD\tnWalkawayC\tnWalkawayD\tnTFTStationary\tnTFTMobile\tnPAVLOVStationary\tnPAVLOVMobile\tnRealisticTFT\tnRetreat");
		return;
	}
	
}
