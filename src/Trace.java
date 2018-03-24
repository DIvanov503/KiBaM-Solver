import java.util.List;

public class Trace {
	public int runIndex;
	public List<Entry> entries;
	
	Trace(int index, List<Entry> entries) {
		this.runIndex = index;
		this.entries = entries;
	}
}
