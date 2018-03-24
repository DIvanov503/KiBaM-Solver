import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.*;

public class TraceExtender {
	protected static String powerName = "OUTPUT_battery_i";
	protected static String availablePrefix = "OUTPUT_battery_a";
	protected static String boundPrefix = "OUTPUT_battery_b";
	protected static Pattern indexPattern = Pattern.compile("\\[(\\d+)\\]");
	protected static Pattern entryPattern = Pattern.compile("\\(([-+]?\\d*\\.?\\d*(?:[eE][-+]?\\d+)?),([-+]?\\d*\\.?\\d*(?:[eE][-+]?\\d+)?)\\)");
	protected static List<Data> dataset = new ArrayList<>();
	protected static List<Data> datasetAvailable = new ArrayList<>();
	protected static List<Data> datasetBound = new ArrayList<>();
	
	protected static double c = 0.3;
	protected static double k = 15;
	protected static double fullCharge = 100000;
	
	protected static int currentChar = '\n';
	protected static String batteryModel = null;
	protected static Map<Double, String> double2state = new HashMap<Double, String>();
	protected static Map<String, String> transition2state = new HashMap<String, String>();
	protected static List<String> states = null;
	protected static ConsumptionTransitionMatrix ctm = null;
	protected static CurrentComposer cc = null;
	protected static String mode = null;
	protected static int degree;
	
	public static void main(String args[]) throws Exception {
		java.io.Reader reader;
		java.io.Writer writer;
		Properties prop = new Properties();
		InputStream input = null;
		String traceFileName = null, statesString = null, csvPath = null;
		try {
			if (args.length >= 1) {
				input = new FileInputStream(args[0]);
			} else {
				input = new FileInputStream("config.properties");
			}
			prop.load(input);
			c = Double.parseDouble(prop.getProperty("c"));
			k = Double.parseDouble(prop.getProperty("k"));
			fullCharge = Double.parseDouble(prop.getProperty("charge"));
			powerName = prop.getProperty("powerName");
			availablePrefix = prop.getProperty("availableChargeName");
			boundPrefix = prop.getProperty("boundChargeName");
			traceFileName = prop.getProperty("trace");
			batteryModel = prop.getProperty("model");
			statesString = prop.getProperty("states");
			mode = prop.getProperty("mode");
			csvPath = prop.getProperty("data");
			degree = Integer.parseInt(prop.getProperty("degree"));
			if (statesString != null) {
				states = Arrays.asList(statesString.split(","));
			}
			for (String state : states) {
				double2state.put(Double.parseDouble(prop.getProperty(state)), state);
				for (String nextState : states) {
					String transition = state + "-" + nextState;
					transition2state.put(transition, prop.getProperty(transition));
				}
			}
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			if (input != null) {
				try {
					input.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
		if (args.length >= 2)
			reader = new InputStreamReader(new FileInputStream(args[1]));
		else if (traceFileName != null)
			reader = new InputStreamReader(new FileInputStream(traceFileName));
		else
			reader = new InputStreamReader(System.in);
		writer = new OutputStreamWriter(System.out);
		readAndWrite(reader, writer);
		reader.close();
		if (mode.equals("constant")) {
			generate();
		} else {
			ctm = new ConsumptionTransitionMatrix(states, csvPath, mode, double2state, degree);
			cc = new CurrentComposer(ctm, transition2state);
			generateInterpolated();
		}
		extend(writer, datasetAvailable, availablePrefix);
		extend(writer, datasetBound, boundPrefix);
		writer.close();
	}

	public static void read(java.io.Reader in) throws Exception {
		BufferedReader reader = new java.io.BufferedReader(in);
		String currentLine;
		// read the input
		currentLine = reader.readLine();
		while (currentLine != null) {
			if (currentLine.startsWith(powerName)) {
				Matcher matcher = indexPattern.matcher(currentLine.substring(powerName.length()));
				matcher.find();
				Data data = new Data(Integer.parseInt(matcher.group(1)), new ArrayList<Trace>());
				dataset.add(data);
				while ((currentLine = reader.readLine()) != null && currentLine.startsWith("[")) {
					matcher = indexPattern.matcher(currentLine);
					matcher.find();
					Trace trace = new Trace(Integer.parseInt(matcher.group(1)), new ArrayList<Entry>());
					data.traces.add(trace);
					matcher = entryPattern.matcher(currentLine);
					while (matcher.find()) {
						trace.entries.add(new Entry(Double.parseDouble(matcher.group(1)),Double.parseDouble(matcher.group(2))));
					}
				}
			} else {
				currentLine = reader.readLine();
			}
		}
	}
	
	public static String readLine(java.io.BufferedReader reader) throws Exception {
		StringBuilder currentLine = new StringBuilder();
		if((char)currentChar == '\n') {
			currentChar = reader.read();
		}
		while (currentChar != -1 && (char)currentChar != '\r' && (char)currentChar != '\n') {
			currentLine.append((char)currentChar);
			currentChar = reader.read();
		}
		if (currentChar == -1) {
			return null;
		}
		if ((char)currentChar == '\r') {
			currentLine.append((char)currentChar);
			currentChar = reader.read();
		}
		if ((char)currentChar == '\n') {
			currentLine.append((char)currentChar);
		}
		return currentLine.toString();
	}
	
	public static void readAndWrite(java.io.Reader in, java.io.Writer out) throws Exception {
		BufferedReader reader = new java.io.BufferedReader(in);
		BufferedWriter writer = new java.io.BufferedWriter(out);
		String currentLine;
		// read the input
		currentLine = readLine(reader);
		while (currentLine != null) {
			if ((currentLine.startsWith(availablePrefix) || currentLine.startsWith(boundPrefix))) {
				while ((currentLine = readLine(reader)) != null && currentLine.startsWith("[")) {
					;
				}
				continue;
			}
			writer.write(currentLine);
			writer.flush();
			if (currentLine.startsWith(powerName)) {
				Matcher matcher = indexPattern.matcher(currentLine.substring(powerName.length()));
				matcher.find();
				Data data = new Data(Integer.parseInt(matcher.group(1)), new ArrayList<Trace>());
				dataset.add(data);
				while ((currentLine = readLine(reader)) != null && currentLine.startsWith("[")) {
					writer.write(currentLine);
					writer.flush();
					matcher = indexPattern.matcher(currentLine);
					matcher.find();
					Trace trace = new Trace(Integer.parseInt(matcher.group(1)), new ArrayList<Entry>());
					data.traces.add(trace);
					matcher = entryPattern.matcher(currentLine);
					while (matcher.find()) {
						trace.entries.add(new Entry(Double.parseDouble(matcher.group(1)),Double.parseDouble(matcher.group(2))));
					}
				}
			} else {
				currentLine = readLine(reader);
			}
		}
	}
	
	public static void generate() throws Exception {
		// extend
		for (Data data : dataset) {
			Data dataAvailable = new Data(data.nodeIndex, new ArrayList<Trace>()),
					dataBound = new Data(data.nodeIndex, new ArrayList<Trace>());
			datasetAvailable.add(dataAvailable);
			datasetBound.add(dataBound);
			for (Trace trace : data.traces) {
				Battery battery = new Battery(c, k, fullCharge);
				Trace traceAvailable = new Trace(trace.runIndex, new ArrayList<Entry>()),
						traceBound = new Trace(trace.runIndex, new ArrayList<Entry>());
				dataAvailable.traces.add(traceAvailable);
				dataBound.traces.add(traceBound);
				traceAvailable.entries.add(new Entry(0, battery.availableCharge));
				traceBound.entries.add(new Entry(0, battery.boundCharge));
				
				Entry previousEntry = trace.entries.get(0);
				int i;
				for (i = 1; i < trace.entries.size(); ++i) {
					Entry entry = trace.entries.get(i);
					if (previousEntry.time >= entry.time) {
						previousEntry = entry;
					} else {
						break;
					}
				}
				for (; i < trace.entries.size() - 1; ++i) {
					Entry entry = trace.entries.get(i);
					// if time doesn't proceed, the current doesn't change, or the entry isn't the last in its time point, skip the entry
					if (entry.time <= previousEntry.time || previousEntry.value == entry.value || trace.entries.get(i + 1).time <= entry.time) {
						continue;
					}
					battery.updateCharge(previousEntry.value, entry.time);
					Entry entryAvailable = new Entry(entry.time, battery.availableCharge),
							entryBound = new Entry(entry.time, battery.boundCharge);
					traceAvailable.entries.add(entryAvailable);
					traceBound.entries.add(entryBound);
					previousEntry = entry;
				}
			}
			
		}
	}
	
	public static void generateInterpolated() throws Exception {
		// extend
		for (Data data : dataset) {
			Data dataAvailable = new Data(data.nodeIndex, new ArrayList<Trace>()),
					dataBound = new Data(data.nodeIndex, new ArrayList<Trace>());
			datasetAvailable.add(dataAvailable);
			datasetBound.add(dataBound);
			for (Trace trace : data.traces) {
				Battery battery = new Battery(c, k, fullCharge);
				Trace traceAvailable = new Trace(trace.runIndex, new ArrayList<Entry>()),
						traceBound = new Trace(trace.runIndex, new ArrayList<Entry>());
				dataAvailable.traces.add(traceAvailable);
				dataBound.traces.add(traceBound);
				traceAvailable.entries.add(new Entry(0, battery.availableCharge));
				traceBound.entries.add(new Entry(0, battery.boundCharge));
				Entry previousEntry = null;
				int i;
				// start with a meaningful current value
				for (i = 0; i < trace.entries.size(); ++i) {
					Entry entry = trace.entries.get(i);
					if (double2state.keySet().contains(entry.value)) {
						previousEntry = entry;
						break;
					}
				}
				for (++i; i < trace.entries.size(); ++i) {
					Entry entry = trace.entries.get(i);
					if (previousEntry.time >= entry.time) {
						previousEntry = entry;
					} else {
						break;
					}
				}
				double previousCurrent = previousEntry.value;
				for (; i < trace.entries.size() - 1; ++i) {
					Entry entry = trace.entries.get(i);
					// if time doesn't proceed, the current doesn't change, or the entry isn't the last in its time point, skip the entry
					if (entry.time <= previousEntry.time || previousEntry.value == entry.value || trace.entries.get(i + 1).time <= entry.time) {
						continue;
					}
					if (double2state.keySet().contains(previousEntry.value)) {
						Current current = cc.compose(previousCurrent, previousEntry.value, entry.value);
						if (mode.equals("polynomial")) {
							battery.updateChargePolynomial(current, entry.time);
						} else if (mode.equals("trigonometric")) {
							battery.updateChargeTrigonometric(current, entry.time);
						}
						Entry entryAvailable = new Entry(entry.time, battery.availableCharge),
								entryBound = new Entry(entry.time, battery.boundCharge);
						traceAvailable.entries.add(entryAvailable);
						traceBound.entries.add(entryBound);
						previousCurrent = previousEntry.value;
						previousEntry = entry;
					} else if (entry.time - previousEntry.time > 0 && previousEntry.value != entry.value) {
						System.err.println("The model stays in an unknown power state " + previousEntry.value + " between " + previousEntry.time + " and " + entry.time + ". Please, declare it in the property file.");
						System.exit(1);
					}
				}
			}
		}
	}
	
	public static void extend(java.io.Writer out, List<Data> dataset, String prefix) throws Exception {
		BufferedWriter writer = new java.io.BufferedWriter(out);
		// extend
		for (Data data : dataset) {
			writer.write(prefix + "[" + data.nodeIndex + "]:");
			writer.newLine();
			for (Trace trace : data.traces) {
				writer.write("[" + trace.runIndex + "]:");
				for (Entry entry : trace.entries) {
					writer.write(" (" + entry.time + "," + entry.value + ")");
				}
				writer.newLine();
			}
		}
		writer.flush();
	}
}
