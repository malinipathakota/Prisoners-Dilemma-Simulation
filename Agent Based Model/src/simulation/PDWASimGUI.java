package simulation;

import java.awt.Color;

import states.GUIStateSparseGrid2D;
import states.SimStateSparseGrid2D;

public class PDWASimGUI extends GUIStateSparseGrid2D {

	public PDWASimGUI(SimStateSparseGrid2D state, int gridWidth, int gridHeight, Color backdrop, Color agentDefaultColor, boolean defaultPortrayal) {
		super(state, gridWidth, gridHeight, backdrop, agentDefaultColor, defaultPortrayal);
	}
	
	public static void main(String[] args) {
		PDWASimGUI.initialize(PDWASim.class, PDWASimGUI.class, 450, 450, Color.BLACK, Color.RED, false);
	}

}
