import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class CurrentComposer {
	Current[][][] matrix;
	protected Map<String, Integer> state2index = null;
	protected Map<Double, Integer> double2index = null;

	CurrentComposer(ConsumptionTransitionMatrix ctm, Map<String, String> transition2state) {
		this.state2index = ctm.state2index;
		this.double2index = ctm.double2index;
		matrix = new Current[state2index.keySet().size()][state2index.keySet().size()][state2index.keySet().size()];
		// fill matrix
		for (String prevState : state2index.keySet()) {
			for (String currentState : state2index.keySet()) {
				for (String nextState : state2index.keySet()) {
					Current current = new Current();
					matrix[state2index.get(prevState)][state2index.get(currentState)][state2index.get(nextState)] = current;
					// add transition from previous state if necessary
					if (!prevState.equals(currentState) && transition2state.get(prevState + "-" + currentState).equals(currentState)){
						current.current.addAll(ctm.getCurrent(prevState, currentState).current);
					}
					// add state itself
					current.current.addAll(ctm.getCurrent(currentState, currentState).current);
					// add transition to next state if necessary
					if (!currentState.equals(nextState) && transition2state.get(currentState + "-" + nextState).equals(currentState)){
						double duration = 0.0;
						Current nextTransition = ctm.getCurrent(currentState, nextState);
						// compute the transition duration
						for (List<Double> spline : nextTransition.current) {
							duration -= spline.get(0);
						}
						// find the steady period spline
						for (int j = 0; j < current.current.size(); ++j) {
							List<Double> spline = current.current.get(j);
							if (spline.get(0) <= 0.0) {
								// create a clone of the spline and update the negated duration
								List<Double> clone = new ArrayList<Double>(spline);
								clone.set(0, spline.get(0) + duration);
								current.current.set(j, clone);
								break;
							}
						}
						// add transition to next state
						current.current.addAll(nextTransition.current);
					}
				}
			}
		}
	}
	
	
	public Current compose(double prevLevel, double currentLevel, double nextLevel) {
		return matrix[double2index.get(prevLevel)][double2index.get(currentLevel)][double2index.get(nextLevel)];
	}
	
	public Current compose(String prevState, String currentState, String nextState) {
		return matrix[state2index.get(prevState)][state2index.get(currentState)][state2index.get(nextState)];
	}
}
