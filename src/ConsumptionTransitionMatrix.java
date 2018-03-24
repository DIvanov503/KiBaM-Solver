import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ConsumptionTransitionMatrix {
	protected Current[][] matrix;
	public Map<String, Integer> state2index = new HashMap<String, Integer>();
	public Map<Double, Integer> double2index = null;
	
	ConsumptionTransitionMatrix(List<String> states, String path, String type, int degree) throws IOException {
		int i = 0;
		for (String state : states) {
			state2index.put(state, i++);
		}
		matrix = new Current[states.size()][states.size()];
		for (String state1 : states) {
			for (String state2 : states) {
				try {
					if (state1.equals(state2)) {
						if (type.equals("polynomial")) {
							matrix[state2index.get(state1)][state2index.get(state2)] = new PolynomialCurrent(path + state1 + ".csv", degree);
						} else if (type.equals("trigonometric")) {
							matrix[state2index.get(state1)][state2index.get(state2)] = new TrigonometricCurrent(path + state1 + ".csv", degree);
						}
					} else {
						if (type.equals("polynomial")) {
							matrix[state2index.get(state1)][state2index.get(state2)] = new PolynomialCurrent(path + state1 + "-" + state2 + ".csv", degree);
						} else if (type.equals("trigonometric")) {
							matrix[state2index.get(state1)][state2index.get(state2)] = new TrigonometricCurrent(path + state1 + "-" + state2 + ".csv", degree);
						}
					}
				} catch (FileNotFoundException e) {
					matrix[state2index.get(state1)][state2index.get(state2)] = null;
					continue;
				}
			}
		}
	}
	
	ConsumptionTransitionMatrix(List<String> states, String path,  String type, Map<Double, String> double2state, int degree) throws IOException {
		this(states, path, type, degree);
		double2index = new HashMap<Double, Integer>();
		for (double current : double2state.keySet()) {
			double2index.put(current, state2index.get(double2state.get(current)));
		}
	}
	
	Current getCurrent(String state1, String state2) {
		return matrix[state2index.get(state1)][state2index.get(state2)];
	}
	
	Current getCurrent(double current1, double current2) {
		return matrix[double2index.get(current1)][double2index.get(current2)];
	}
}
