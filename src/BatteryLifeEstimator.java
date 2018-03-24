import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BatteryLifeEstimator extends TraceExtender {
	protected static double multiplier = 10000;
	protected static double demultiplier = 10;
	protected static int steps = 5;
	
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
		read(reader);
		reader.close();
		if (mode.equals("constant")) {
			if (batteryModel.equals("naive"))
				generateNaive();
			else
				generate();
		} else {
			ctm = new ConsumptionTransitionMatrix(states, csvPath, mode, double2state, degree);
			cc = new CurrentComposer(ctm, transition2state);
			generateInterpolated();
		}
		writer.close();
	}

	public static void generate() throws Exception {
		for (Data data : dataset) {
			Data dataAvailable = new Data(data.nodeIndex, new ArrayList<Trace>()),
					dataBound = new Data(data.nodeIndex, new ArrayList<Trace>());
			datasetAvailable.add(dataAvailable);
			datasetBound.add(dataBound);
			for (Trace trace : data.traces) {
				Battery battery = new Battery(c, k, fullCharge);
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
				for (; i < trace.entries.size(); ++i) {
					Entry entry = trace.entries.get(i);
					// if time doesn't proceed, the current doesn't change, or the entry isn't the last in its time point, skip the entry
					if (entry.time <= previousEntry.time || previousEntry.value == entry.value || trace.entries.get(i + 1).time <= entry.time) {
						continue;
					}
					battery.updateCharge(previousEntry.value, entry.time);
					previousEntry = entry;
				}
				outerloop:
				while (battery.availableCharge > 0) {
					previousEntry = trace.entries.get(trace.entries.size() / 2);
					for (i = trace.entries.size() / 2 + 1; i < trace.entries.size(); ++i) {
						Entry entry = trace.entries.get(i);
						if (previousEntry.time >= entry.time) {
							previousEntry = entry;
						} else {
							break;
						}
					}
					for (; i < trace.entries.size(); ++i) {
						double base = battery.time, previousAvailable = battery.availableCharge, previousBound = battery.boundCharge;
						Entry entry = trace.entries.get(i);
						if (entry.time <= previousEntry.time || previousEntry.value == entry.value || trace.entries.get(i + 1).time <= entry.time) {
							continue;
						}
						battery.updateCharge(previousEntry.value, base + (entry.time - previousEntry.time));
						if  (battery.availableCharge < 0) {
							double dt = (entry.time - previousEntry.time) / 2, step = dt / 2;
							do {
								battery.time = base;
								battery.availableCharge = previousAvailable;
								battery.boundCharge = previousBound;
								battery.updateCharge(previousEntry.value, base + dt);
								if (battery.availableCharge < 0) {
									dt -= step;
								} else {
									dt += step;
								}
								step /= 2;
							} while (step > 0.01);
							System.out.println(battery.time);
							break outerloop;
						}
						previousEntry = entry;
					}
				}
				System.out.println(battery.time);
			}
		}
	}
	
	public static void generateNaive() throws Exception {
		// extend
		for (Data data : dataset) {
			for (Trace trace : data.traces) {
				Naive battery = new Naive(fullCharge);
				Entry previousEntry = trace.entries.get(0);
				double halfEnergy = 0;
				for (int i = 1; i < trace.entries.size(); ++i) {
					Entry currentEntry = trace.entries.get(i);
					battery.updateCharge(previousEntry.value, currentEntry.time);
					if (i > trace.entries.size() / 2)
						halfEnergy += (currentEntry.time - previousEntry.time) * (previousEntry.value);
					previousEntry = currentEntry;
				}
				double halfTime = trace.entries.get(trace.entries.size() - 1).time - trace.entries.get(trace.entries.size() / 2).time,
						times = (battery.fullCharge - battery.fullCharge % halfEnergy) / halfEnergy;
				battery.time += halfTime * times;
				battery.fullCharge = battery.fullCharge % halfEnergy;
				previousEntry = trace.entries.get(trace.entries.size() / 2);
				for (int i = trace.entries.size() / 2 + 1; i < trace.entries.size(); ++i) {
					double base = battery.time, previousCharge = battery.fullCharge;
					Entry currentEntry = trace.entries.get(i);
					battery.updateCharge(previousEntry.value, base + (currentEntry.time - previousEntry.time));
					if  (battery.fullCharge < 0) {
						battery.time = base;
						battery.fullCharge = previousCharge;
						battery.updateCharge(previousEntry.value, base + previousCharge / previousEntry.value);
						break;
					}
					previousEntry = currentEntry;
				}
				System.out.println(battery.time);
			}
		}
	}
	
	public static void generateInterpolated() throws Exception {
		for (Data data : dataset) {
			for (Trace trace : data.traces) {
				Battery battery = new Battery(c, k, fullCharge);
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
				for (; i < trace.entries.size(); ++i) {
					Entry entry = trace.entries.get(i);
					//System.out.println(i + " " + entry.time);
					// if time doesn't proceed, the current doesn't change, or the entry isn't the last in its time point, skip the entry
					if (entry.time <= previousEntry.time || previousEntry.value == entry.value || trace.entries.get(i + 1).time <= entry.time) {
						continue;
					}
					if (double2state.keySet().contains(previousEntry.value)) {
						//System.out.println(previousEntry.value + ", " + battery.time + " - " + entry.time);
						//System.out.println(previousEntry.value + " --> " + entry.value);
						Current current = cc.compose(previousCurrent, previousEntry.value, entry.value);
						if (mode.equals("polynomial")) {
							battery.updateChargePolynomial(current, entry.time);
						} else if (mode.equals("trigonometric")) {
							battery.updateChargeTrigonometric(current, entry.time);
						}
						previousCurrent = previousEntry.value;
						previousEntry = entry;
					} else if (entry.time - previousEntry.time > 0 && previousEntry.value != entry.value) {
						System.err.println("The model stays in an unknown power state " + previousEntry.value + " between " + previousEntry.time + " and " + entry.time + ". Please, declare it in the property file.");
						System.exit(1);
					}
					//System.out.println(battery.availableCharge);
				}
				//System.out.println(battery.time + " " + battery.availableCharge);
				//System.out.println(traceAvailable.entries.get(traceAvailable.entries.size() - 1).value + " " + traceBound.entries.get(traceBound.entries.size() - 1).value);
				//System.out.println(data.nodeIndex + " " + battery.time + " " + trace.entries.size());
				outerloop:
				while (battery.availableCharge > 0) {
					//System.out.println(battery.time);
					previousEntry = trace.entries.get(trace.entries.size() / 2);
					for (i = trace.entries.size() / 2 + 1; i < trace.entries.size(); ++i) {
						Entry entry = trace.entries.get(i);
						if (previousEntry.time >= entry.time) {
							previousEntry = entry;
						} else {
							break;
						}
					}
					previousCurrent = previousEntry.value;
					for (; i < trace.entries.size(); ++i) {
						double base = battery.time, previousAvailable = battery.availableCharge, previousBound = battery.boundCharge;
						Entry entry = trace.entries.get(i);
						//System.out.println(i + " " + entry.time);
						// if time doesn't proceed, the current doesn't change, or the entry isn't the last in its time point, skip the entry
						if (entry.time <= previousEntry.time || previousEntry.value == entry.value || trace.entries.get(i + 1).time <= entry.time) {
							continue;
						}
						if (double2state.keySet().contains(previousEntry.value)) {
							//System.out.println(previousEntry.value + ", " + battery.time + " - " + entry.time);
							//System.out.println(previousEntry.value + " --> " + entry.value);
							Current current = cc.compose(previousCurrent, previousEntry.value, entry.value);
							if (mode.equals("polynomial")) {
								battery.updateChargePolynomial(current, base + (entry.time - previousEntry.time));
							} else if (mode.equals("trigonometric")) {
								battery.updateChargeTrigonometric(current, base + (entry.time - previousEntry.time));
							}
							previousCurrent = previousEntry.value;
							previousEntry = entry;
						} else if (entry.time - previousEntry.time > 0 && previousEntry.value != entry.value) {
							System.err.println("The model stays in an unknown power state " + previousEntry.value + " between " + previousEntry.time + " and " + entry.time + ". Please, declare it in the property file.");
							System.exit(1);
						}
						if  (battery.availableCharge < 0) {
							battery.time = base;
							battery.availableCharge = previousAvailable;
							battery.boundCharge = previousBound;
							System.out.println(battery.time);
							break outerloop;
						}
					}
				}
			}
		}
	}
}
