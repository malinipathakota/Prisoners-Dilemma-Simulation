package simulation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import agents.Agent;
import agents.Observer;
import agents.Strategy;
import sim.field.grid.Grid2D;
import sim.util.Bag;
import sim.util.Int2D;
import sim.util.IntBag;
import states.SimStateSparseGrid2D;

public class PDWASim extends SimStateSparseGrid2D {
	
	private int gridWidth = 200;
	private int gridHeight = 200;
	private int nNaiveC = 100;
	private int nNaiveD = 100;
	private int nWalkawayC = 100;
	private int nWalkawayD = 100;
	private int nTFTStationary = 0;
	private int nTFTMobile = 0;
	private int nPAVLOVStationary = 0;
	private int nPAVLOVMobile = 0;
	private int nRealisticTFT = 0;
	private int nRetreat = 0;
	private int playRadius = 1;
	private double errorRate = 0.001;
	private double probRandomMove = 1.0;
	private boolean localReproduction = false;
	private int reproductionRadius = 1;
	private boolean enforceCapAfterReproduction = false;
	private int populationCap;
	
	public PDWASim(long seed) {
		super(seed);
	}
	
	public void start() {
		super.start();
		makeSpace(gridWidth, gridHeight);
		makeAgents();
		makeObserver();
		return;
	}
	
	/**
	 * Make all required agents in the specified quantities. Also sets the population cap to the sum total of the number of initial agents.
	 */
	protected void makeAgents() {
		populationCap = nNaiveC + nNaiveD + nWalkawayC + nWalkawayD + nTFTStationary + nTFTMobile + nPAVLOVStationary + nPAVLOVMobile + nRealisticTFT + nRetreat;	// we freeze this so it can't be changed while running
		for (int i = 0; i < nNaiveC; i++) {
			makeAgent(Strategy.NAIVE_C);
		}
		for (int i = 0; i < nNaiveD; i++) {
			makeAgent(Strategy.NAIVE_D);
		}
		for (int i = 0; i < nWalkawayC; i++) {
			makeAgent(Strategy.WALKAWAY_C);
		}
		for (int i = 0; i < nWalkawayD; i++) {
			makeAgent(Strategy.WALKAWAY_D);
		}
		for (int i = 0; i < nTFTStationary; i++) {
			makeAgent(Strategy.TFT_STATIONARY);
		}
		for (int i = 0; i < nTFTMobile; i++) {
			makeAgent(Strategy.TFT_MOBILE);
		}
		for (int i = 0; i < nPAVLOVStationary; i++) {
			makeAgent(Strategy.PAVLOV_STATIONARY);
		}
		for (int i = 0; i < nPAVLOVMobile; i++) {
			makeAgent(Strategy.PAVLOV_MOBILE);
		}
		for (int i = 0; i < nRealisticTFT; i++) {
			makeAgent(Strategy.REALISTIC_TFT);
		}
		for(int i = 0; i < nRetreat; i++) {
			makeAgent(Strategy.RETREAT);
		}
		return;
	}
	
	/**
	 * Make a new agent with the given strategy.
	 * @param strat game strategy
	 * @return the new agent
	 */
	public Agent makeAgent(Strategy strat) {
		int x, y;
		Bag test;
		do {
			x = random.nextInt(gridWidth);
			y = random.nextInt(gridHeight);
			test = space.getObjectsAtLocation(x, y);
		} while (test != null && test.numObjs != 0);
		Agent a = new Agent(this, x, y, strat);
		RGBTColor col = colorByStrategy(strat);
		gui.setOvalPortrayal2DColor(a, col.red, col.green, col.blue, col.alpha);
		a.attachStopper(schedule.scheduleRepeating(a));
		space.setObjectLocation(a, x, y);
		return a;
	}
	
	public Agent makeAgentNear(int x, int y, int radius, Strategy strat) {
		Int2D location = getEmptyLocationNear(x, y, radius);
		if (location == null) {									// if there are no empty locations, we return null
			return null;
		}
		int newx = location.x;
		int newy = location.y;
		Agent a = new Agent(this, newx, newy, strat);
		RGBTColor col = colorByStrategy(strat);
		gui.setOvalPortrayal2DColor(a, col.red, col.green, col.blue, col.alpha);
		a.attachStopper(schedule.scheduleRepeating(a));
		space.setObjectLocation(a, newx, newy);
		return a;
	}
	
	/**
	 * Returns a random, empty location within <i>radius</i> units of the given (<i>x</i>, <i>y</i>) location, or null if there are no empty locations nearby. Can return the location (<i>x</i>, <i>y</i>) if it
	 * is empty.
	 * @param x x-coordinate to look around
	 * @param y y-coordinate to look around
	 * @param radius distance from (x, y) to search
	 * @return random, empty location or null
	 */
	public Int2D getEmptyLocationNear(int x, int y, int radius) {
		Bag test;
		IntBag xlocs = new IntBag();
		IntBag ylocs = new IntBag();
		space.getMooreLocations(x, y, radius, Grid2D.TOROIDAL, true, xlocs, ylocs);
		int size = xlocs.numObjs;
		ArrayList<Integer> selector = new ArrayList<>();
		for (int i = 0; i < size; i++) {
			selector.add(new Integer(i));
		}
		Collections.shuffle(selector);		// NOTE NOTE NOTE! This does not use the MASON random number generator, which means this isn't replicable, but we will let it go for simplicity's sake
		for (int i = 0; i < size; i++) {			// we step through the now randomly-ordered list of indexes into the available locations and return the first empty one
			int index = selector.get(i);
			test = space.getObjectsAtLocation(xlocs.objs[index], ylocs.objs[index]);
			if (test == null || test.numObjs == 0) {
				return new Int2D(xlocs.objs[index], ylocs.objs[index]);
			}
		}
		return null;		// if we've gotten to this point, there are no empty locations, so we return null
	}
	
	/**
	 * Get a color description for an agent given its strategy.
	 * @param strat the agent's strategy
	 * @return the color description
	 */
	private RGBTColor colorByStrategy(Strategy strat) {
		switch (strat) {
			case NAIVE_C:
				return new RGBTColor(0, 1, 0, 1);
			case NAIVE_D:
				return new RGBTColor(1, 0, 0, 1);
			case PAVLOV_MOBILE:
				return new RGBTColor(1, .5, 0, 1);
			case PAVLOV_STATIONARY:
				return new RGBTColor(1, 1, 0, 1);
			case TFT_MOBILE:
				return new RGBTColor(1, 1, 1, 1);
			case TFT_STATIONARY:
				return new RGBTColor(.5, .5, .5, 1);
			case WALKAWAY_C:
				return new RGBTColor(0, 0, 1, 1);
			case WALKAWAY_D:
				return new RGBTColor(1, 0, 1, 1);
			case REALISTIC_TFT:
				return new RGBTColor(1, .5, .5, 1);
			default:
		}
		return new RGBTColor(0, 1, 1, 1);
	}
	
	/**
	 * Make the observer for data output (and resetting agents at the end of each time step).
	 */
	protected void makeObserver() {
		Observer o = new Observer(this);
		o.attachStopper(schedule.scheduleRepeating(0, 100, o));
		return;
	}
	
	public int acquirePopulationCap() {
		return populationCap;
	}

	public int getGridWidth() {
		return gridWidth;
	}

	public void setGridWidth(int gridWidth) {
		this.gridWidth = gridWidth;
	}

	public int getGridHeight() {
		return gridHeight;
	}

	public void setGridHeight(int gridHeight) {
		this.gridHeight = gridHeight;
	}

	public int getnNaiveC() {
		return nNaiveC;
	}

	public void setnNaiveC(int nNaiveC) {
		this.nNaiveC = nNaiveC;
	}

	public int getnNaiveD() {
		return nNaiveD;
	}

	public void setnNaiveD(int nNaiveD) {
		this.nNaiveD = nNaiveD;
	}

	public int getnWalkawayC() {
		return nWalkawayC;
	}

	public void setnWalkawayC(int nWalkawayC) {
		this.nWalkawayC = nWalkawayC;
	}

	public int getnWalkawayD() {
		return nWalkawayD;
	}

	public void setnWalkawayD(int nWalkawayD) {
		this.nWalkawayD = nWalkawayD;
	}

	public int getnTFTStationary() {
		return nTFTStationary;
	}

	public void setnTFTStationary(int nTFTStationary) {
		this.nTFTStationary = nTFTStationary;
	}

	public int getnTFTMobile() {
		return nTFTMobile;
	}

	public void setnTFTMobile(int nTFTMobile) {
		this.nTFTMobile = nTFTMobile;
	}

	public int getnPAVLOVStationary() {
		return nPAVLOVStationary;
	}

	public void setnPAVLOVStationary(int nPAVLOVStationary) {
		this.nPAVLOVStationary = nPAVLOVStationary;
	}

	public int getnPAVLOVMobile() {
		return nPAVLOVMobile;
	}

	public void setnPAVLOVMobile(int nPAVLOVMobile) {
		this.nPAVLOVMobile = nPAVLOVMobile;
	}

	public int getnRealisticTFT() {
		return nRealisticTFT;
	}

	public void setnRealisticTFT(int nRealisticTFT) {
		this.nRealisticTFT = nRealisticTFT;
	}

	public int getPlayRadius() {
		return playRadius;
	}

	public void setPlayRadius(int playRadius) {
		this.playRadius = playRadius;
	}

	public double getErrorRate() {
		return errorRate;
	}

	public void setErrorRate(double errorRate) {
		this.errorRate = errorRate;
	}

	public double getProbRandomMove() {
		return probRandomMove;
	}

	public void setProbRandomMove(double probRandomMove) {
		this.probRandomMove = probRandomMove;
	}
	
	public boolean isLocalReproduction() {
		return localReproduction;
	}

	public void setLocalReproduction(boolean localReproduction) {
		this.localReproduction = localReproduction;
	}

	public int getReproductionRadius() {
		return reproductionRadius;
	}

	public void setReproductionRadius(int reproductionRadius) {
		this.reproductionRadius = reproductionRadius;
	}

	/**
	 * Convenience class to represent a complete description of an agent's color without having to be careful about numeric types. Alpha is the inverse of transparency: 0 is completely clear and 1 is completely solid.
	 * @author Matt L. Miller
	 */
	private class RGBTColor {
		float red;
		float green;
		float blue;
		float alpha;
		
		public RGBTColor(double red, double green, double blue, double alpha) {
			this.red = (float)red;
			this.green = (float)green;
			this.blue = (float)blue;
			this.alpha = (float)alpha;
		}
	}

	public int getnRetreat() {
		return nRetreat;
	}

	public void setnRetreat(int nRetreat) {
		this.nRetreat = nRetreat;
	}

	public boolean isEnforceCapAfterReproduction() {
		return enforceCapAfterReproduction;
	}

	public void setEnforceCapAfterReproduction(boolean enforceCapAfterReproduction) {
		this.enforceCapAfterReproduction = enforceCapAfterReproduction;
	}

}
