import java.util.List;

public class Data {
	public int nodeIndex;
	public List<Trace> traces;
	
	Data(int index, List<Trace> traces) {
		this.nodeIndex = index;
		this.traces = traces;
	}
}
