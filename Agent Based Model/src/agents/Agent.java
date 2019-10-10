package agents;

import java.util.Random;

import agents.Agent.Action;
import agents.Agent.StrategySet;
import sim.engine.SimState;
import sim.engine.Steppable;
import sim.engine.Stoppable;
import sim.field.grid.Grid2D;
import sim.field.grid.SparseGrid2D;
import sim.util.Bag;
import simulation.PDWASim;

public class Agent implements Steppable {
	
	private boolean played;
	private int x, y;
	private int dirx, diry;
	private double resources;
	private int id;
	private GameMemory lastGame;
	
	private Strategy strategy;
	private Stoppable stopper;
	
	private PDWASim sim;
	private SparseGrid2D space;
	
	private static int nextId = 0;
	
	private boolean defected = false;
	
	public Agent(PDWASim sim, int x, int y, Strategy strategy) {
		this.sim = sim;
		space = sim.acquireSpace();
		this.x = x;
		this.y = y;
		randomizeMovement();
		this.strategy = strategy;
		resources = sim.random.nextInt(40) + 10;	// uniformly distributed in [10, 49]
		played = false;
		lastGame = new GameMemory(Action.NOTHING, Action.NOTHING);
		id = nextId++;
	}
	
	@Override
	public void step(SimState state) {
		if (played) {					// if we have been played as a partner already in this move, we're done
			return;
		}
		Bag neighbors = space.getMooreNeighbors(x, y, sim.getPlayRadius(), Grid2D.TOROIDAL, true);
		Agent partner = pickPartner(neighbors);
		if (playAndDecideMove(partner)) {
			moveLogic();
		}
		updateLifeEvents();
		return;
	}
	
	/**
	 * Set new random direction for agent.
	 */
	private void randomizeMovement() {
		dirx = sim.random.nextInt(3) - 1;
		diry = sim.random.nextInt(3) - 1;
		return;
	}
	
	/**
	 * Everything necessary for movement. Includes directional adjustments and the move method itself.
	 */
	private void moveLogic() {
		if (sim.random.nextBoolean(sim.getProbRandomMove())) {
			randomizeMovement();
		}
		move();
		return;
	}
	
	/**
	 * Move the agent to a new location based on its current direction, wrapping for toroidal space and avoiding collisions with other agents.
	 */
	private void move() {
		int tempx = space.stx(x + dirx);
		int tempy = space.sty(y + diry);
		if (space.getObjectsAtLocation(tempx, tempy) != null) {
			dirx = -dirx;
			diry = -diry;
			tempx = x;
			tempy = y;
		}
		x = tempx;
		y = tempy;
		space.setObjectLocation(this, x, y);
		return;
	}
	
	/**
	 * Pick a random neighbor to play with. Only returns an agent that has not yet played this round. Will return null if there are no available agents.
	 * @param candidates agents to pick from; can include self (but self will not be returned)
	 * @return a random, unplayed agent from among the candidates, or null if there aren't any
	 */
	private Agent pickPartner(Bag candidates) {
		if (candidates == null || candidates.isEmpty()) {			// no neighbors, no partner to play with
			return null;
		}
		int size = candidates.numObjs;
		int rand = sim.random.nextInt(size);
		for (int i = rand; i < size; i++) {
			Object o = candidates.objs[i];
			if (o == null || o == this) {
				continue;
			}
			Agent a = (Agent)o;
			if (!a.played) {
				return a;
			}
		}
		for (int i = 0; i < rand; i++) {				// if we didn't find one before the end, let's look starting at the beginning
			Object o = candidates.objs[i];
			if (o == null || o == this) {
				continue;
			}
			Agent a = (Agent)o;
			if (!a.played) {
				return a;
			}
		}
		return null;									// if we've made it this far, there are no good candidates
	}
	
	/**
	 * Play the dictator game with a partner, returning true if the agent should move due to strategy or because no partner was found. The partner agent will automatically be played and moved and its played flag will
	 * be set so it can not be played again in this step. If null is passed, this will be interpreted as indicating no partners were available, and the appropriate value for movement based on the agent's strategy will
	 * be returned.
	 * @param partner agent to play with or null if none are available
	 * @return true if this agent should move
	 */
	private boolean playAndDecideMove(Agent partner) {
		if (partner == null) {
			lastGame = new GameMemory(Action.NOTHING, Action.NOTHING);	// nothing happens if there are no partners
			switch (strategy) {
				case NAIVE_C:
				case NAIVE_D:
				case PAVLOV_MOBILE:
				case TFT_MOBILE:
				case WALKAWAY_C:
				case WALKAWAY_D:
				case REALISTIC_TFT:
				case RETREAT:
					return true;			// all of the preceding strategies move if no partner is found
				case PAVLOV_STATIONARY:
				case TFT_STATIONARY:
					return false;			// these strategies do nothing if no partner is found
			}
			throw new RuntimeException("This agent is using a strategy that has not been implemented: " + strategy);
		}
		StrategySet mySet = selectAction(partner);
		StrategySet partnerSet = partner.selectAction(this);
		Action sAct = mySet.action;
		Action pAct = partnerSet.action;
		playPD(partner, sAct, pAct);
		// the partner will need to move now if they are going to move at all, since they are now marked played (and might have already taken their step anyway)
		if (partnerSet.moveCooperate && sAct.equals(Action.COOPERATE) || partnerSet.moveDefect && sAct.equals(Action.DEFECT) || partnerSet.moveNothing && sAct.equals(Action.NOTHING)) {
			partner.moveLogic();
		}
		// now we return our own decision
		return mySet.moveCooperate && pAct.equals(Action.COOPERATE) || mySet.moveDefect && pAct.equals(Action.DEFECT) || mySet.moveNothing && pAct.equals(Action.NOTHING);
	}
	
	/**
	 * Returns this agent's action set (including game strategy and movement options given partner's action) based on this agent's strategy and history.
	 * @param partner the partner this agent will be playing with
	 * @return strategy set for this move
	 */
	private StrategySet selectAction(Agent partner) {	// perhaps partner will be useful for future strategies
		switch (strategy) {
			case NAIVE_C:
				return playNaiveC();
			case NAIVE_D:
				return playNaiveD();
			case PAVLOV_MOBILE:
				return playPavlovMobile();
			case PAVLOV_STATIONARY:
				return playPavlovStationary();
			case TFT_MOBILE:
				return playTFTMobile();
			case TFT_STATIONARY:
				return playTFTStationary();
			case WALKAWAY_C:
				return playWalkawayC();
			case WALKAWAY_D:
				return playWalkawayD();
			case REALISTIC_TFT:
				return playRealisticTFT();
			case RETREAT:
				return playRetreat();
		}
		throw new RuntimeException("This agent is using a strategy that has not been implemented: " + strategy);
	}
	
	/**
	 * Return strategy set for naive cooperator.
	 * @return strategy set
	 */
	private StrategySet playNaiveC() {
		return new StrategySet(Action.COOPERATE, false, false, true);
	}
	
	/**
	 * Return strategy set for naive defector.
	 * @return strategy set
	 */
	private StrategySet playNaiveD() {
		return new StrategySet(Action.DEFECT, false, false, true);
	}
	
	/**
	 * Return strategy set for mobile PAVLOV.
	 * @return strategy set
	 */
	private StrategySet playPavlovMobile() {
		if (lastGame.self.equals(Action.COOPERATE) && lastGame.other.equals(Action.COOPERATE)) {
			return new StrategySet(Action.COOPERATE, false, false, true);
		}
		if (lastGame.self.equals(Action.COOPERATE) && lastGame.other.equals(Action.DEFECT)) {
			return new StrategySet(Action.DEFECT, false, false, true);
		}
		if (lastGame.self.equals(Action.DEFECT) && lastGame.other.equals(Action.COOPERATE)) {
			return new StrategySet(Action.DEFECT, false, false, true);
		}
		if (lastGame.self.equals(Action.DEFECT) && lastGame.other.equals(Action.DEFECT)) {
			return new StrategySet(Action.COOPERATE, false, false, true);
		}		// else there must be a NOTHING in one of the spots, which implies ... 
		return new StrategySet(Action.COOPERATE, false, false, true);
	}
	
	/**
	 * Return strategy set for stationary PAVLOV.
	 * @return strategy set
	 */
	private StrategySet playPavlovStationary() {
		if (lastGame.self.equals(Action.COOPERATE) && lastGame.other.equals(Action.COOPERATE)) {
			return new StrategySet(Action.COOPERATE, false, false, false);
		}
		if (lastGame.self.equals(Action.COOPERATE) && lastGame.other.equals(Action.DEFECT)) {
			return new StrategySet(Action.DEFECT, false, false, false);
		}
		if (lastGame.self.equals(Action.DEFECT) && lastGame.other.equals(Action.COOPERATE)) {
			return new StrategySet(Action.DEFECT, false, false, false);
		}
		if (lastGame.self.equals(Action.DEFECT) && lastGame.other.equals(Action.DEFECT)) {
			return new StrategySet(Action.COOPERATE, false, false, false);
		}		// else there must be a NOTHING in one of the spots, which implies ... 
		return new StrategySet(Action.COOPERATE, false, false, false);
	}
	
	/**
	 * Return strategy set for realistic tit-for-tat (also known as walkaway tit-for-tat).
	 * @return strategy set
	 */
	private StrategySet playRealisticTFT() {
		if (lastGame.other.equals(Action.DEFECT)) {						// we can just test for DEFECT ...
			return new StrategySet(Action.DEFECT, false, true, true);
		}
		return new StrategySet(Action.COOPERATE, false, true, true);	// ... because this is the return value for both COOPERATE and NOTHING
	}
	
	/**
	 * Return strategy set for mobile tit-for-tat.
	 * @return strategy set
	 */
	private StrategySet playTFTMobile() {
		if (lastGame.other.equals(Action.COOPERATE)) {
			return new StrategySet(Action.COOPERATE, false, false, true);
		}
		if (lastGame.other.equals(Action.DEFECT)) {
			return new StrategySet(Action.DEFECT, false, false, true);
		}	// then it must be NOTHING
		return new StrategySet(Action.COOPERATE, false, false, true);
	}
	
	/**
	 * Return strategy set for stationary tit-for-tat.
	 * @return strategy set
	 */
	private StrategySet playTFTStationary() {
		if (lastGame.other.equals(Action.COOPERATE)) {
			return new StrategySet(Action.COOPERATE, false, false, false);
		}
		if (lastGame.other.equals(Action.DEFECT)) {
			return new StrategySet(Action.DEFECT, false, false, false);
		}	// this it must be NOTHING
		return new StrategySet(Action.COOPERATE, false, false, false);
	}
	
	/**
	 * Return strategy set for walk-away cooperator.
	 * @return strategy set
	 */
	private StrategySet playWalkawayC() {
		return new StrategySet(Action.COOPERATE, false, true, true);
	}
	
	/**
	 * Return strategy set for walk-away defector.
	 * @return strategy set
	 */
	private StrategySet playWalkawayD() {
		return new StrategySet(Action.DEFECT, false, true, true);
	}
	
	// RETREAT Strategy
	private StrategySet playRetreat() {
		if(defected == false) {
			if (lastGame.other.equals(Action.COOPERATE)) {
				return new StrategySet(Action.COOPERATE, false, true, true);
			}
			if (lastGame.other.equals(Action.DEFECT)) {
				defected = true;
				return new StrategySet(Action.DEFECT, false, true, true);
			}
			return new StrategySet(Action.COOPERATE, false, true, true);
		}
		return new StrategySet(Action.DEFECT, true, true, true);
	}
	
	/**
	 * Play the prisoner's dilemma game with the given partner using the strategies provided. Updates resources for both agents, sets played flag, and stores memory of last game play. This also introduces the specified
	 * error rate in both players' actions.
	 * @param partner agent to play with
	 * @param myAction this agent's action
	 * @param partnerAction partner's action
	 */
	private void playPD(Agent partner, Action myAction, Action partnerAction) {
		myAction = introduceError(myAction);
		partnerAction = introduceError(partnerAction);
		if (myAction.equals(Action.COOPERATE) && partnerAction.equals(Action.COOPERATE)) {
			resources += 3;
			partner.resources += 3;
		} else if (myAction.equals(Action.COOPERATE) && partnerAction.equals(Action.DEFECT)) {
			resources -= 1;
			partner.resources += 5;
		} else if (myAction.equals(Action.DEFECT) && partnerAction.equals(Action.COOPERATE)) {
			resources += 5;
			partner.resources -= 1;
		}		// must be DEFECT/DEFECT, which means no change
		played = true;
		partner.played = true;
		lastGame = new GameMemory(myAction, partnerAction);
		partner.lastGame = new GameMemory(partnerAction, myAction);
		return;
	}
	
	/**
	 * Return the same action, usually, but flips it at the simulation's specified error rate. The action NOTHING is always unchanged.
	 * @param a specified action
	 * @return given action with simulated error in execution
	 */
	private Action introduceError(Action a) {
		if (sim.random.nextBoolean(sim.getErrorRate())) {
			if (a.equals(Action.COOPERATE)) {		// we only flip C and D -- NOTHING gets left alone
				a = Action.DEFECT;
			}
			if (a.equals(Action.DEFECT)) {
				a = Action.COOPERATE;
			}
		}
		return a;
	}
	
	/**
	 * Based on the current resources, implements reproduction and death.
	 */
	private void updateLifeEvents() {
		if (resources <= 0) {
			remove();
		} else if (resources >= 100) {
			reproduce();
		}
		return;
	}
	
	/**
	 * Reproduce a new agent if there is space in the simulation (that is, the population capacity has not been reached). Resources are divided evenly between the parent and the offspring.
	 */
	private void reproduce() {
		if (space.allObjects.numObjs >= sim.acquirePopulationCap()) {		// if we're at the population cap, we'll have to wait to reproduce
			if(sim.isEnforceCapAfterReproduction()) {
				Agent o;
				if (sim.isLocalReproduction()) {
					o = sim.makeAgentNear(x, y, sim.getReproductionRadius(), strategy);
				} else {
					o = sim.makeAgent(strategy);
				}
				if(o == null)
					return;
				
				Bag objBag = space.getAllObjects();
				int total = objBag.numObjs;
				Agent temp = (Agent)(objBag.objs[sim.random.nextInt(total)]);
				if(temp != null)
					temp.remove();
			}
			else {
				return;
			}
		}
		Agent o;
		if (sim.isLocalReproduction()) {
			o = sim.makeAgentNear(x, y, sim.getReproductionRadius(), strategy);
		} else {
			o = sim.makeAgent(strategy);
		}
		if(o == null)
			return;
		
		double split = resources / 2;
		o.resources = resources - split;
		resources = split;
		return;
	}
	
	/**
	 * Remove this agent from the simulation; simulated death.
	 */
	private void remove() {
		space.remove(this);
		stopper.stop();
		return;
	}
	
	/**
	 * Attach the stopper that allows this agent to be removed from the schedule.
	 * @param stopper stoppable object returned by schedule when adding a repeating object
	 */
	public void attachStopper(Stoppable stopper) {
		this.stopper = stopper;
		return;
	}
	
	/**
	 * Reset this agent's played flag so it can play on the next time step; called by observer at the end of every step.
	 */
	public void reset() {
		played = false;
		return;
	}

	/**
	 * Has this agent played (or played with another agent) in the current time step?
	 * @return true if agent has played this time step
	 */
	public boolean isPlayed() {
		return played;
	}

	/**
	 * Get this agent's current x coordinate.
	 * @return x-coordinate
	 */
	public int getX() {
		return x;
	}

	/**
	 * Get this agent's current y coordinate.
	 * @return y-coordinate
	 */
	public int getY() {
		return y;
	}

	/**
	 * Get this agent's current x-axis direction.
	 * @return x direction
	 */
	public int getDirx() {
		return dirx;
	}

	/**
	 * Gets this agent's current y-axis direction.
	 * @return y direction
	 */
	public int getDiry() {
		return diry;
	}

	/**
	 * Gets this agent's current resource level.
	 * @return resources
	 */
	public double getResources() {
		return resources;
	}

	/**
	 * Get this agent's unique ID number.
	 * @return ID number
	 */
	public int getId() {
		return id;
	}

	/**
	 * Get this agent's strategy.
	 * @return game strategy
	 */
	public Strategy getStrategy() {
		return strategy;
	}
	
	public GameMemory getMemory() {
		return lastGame;
	}
	
	public String toString() {
		return "PDWA Agent at (" + x + ", " + y + "): ID=" + id + "; strategy=" + strategy + "; resources=" + resources + "; played=" + played + "; dirx=" + dirx + "; diry=" + diry + ".";
	}
	
	public enum Action {
		COOPERATE,
		DEFECT,
		NOTHING
	}
	
	/**
	 * Complete description of an agent's strategy at a given time step. Includes the action (cooperate or defect), and whether or not the agent will move based on its partners actions in the current game.
	 * @author Matt L. Miller
	 */
	public class StrategySet {
		Action action;
		boolean moveCooperate;
		boolean moveDefect;
		boolean moveNothing;
		
		public StrategySet(Action action, boolean moveCooperate, boolean moveDefect, boolean moveNothing) {
			this.action = action;
			this.moveCooperate = moveCooperate;
			this.moveDefect = moveDefect;
			this.moveNothing = moveNothing;
		}
	}
	
	/**
	 * A memory package of a single game play. Includes the agent's own action and its partner's action.
	 * @author Matt L. Miller
	 */
	public class GameMemory {
		Action self;
		Action other;
		
		public GameMemory(Action self, Action other) {
			this.self = self;
			this.other = other;
		}
		
		public String toString() {
			return "Memory of " + self + "/" + other + ".";
		}
	}
	
}
