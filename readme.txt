This disk contains two executable jar archives and source code.

The command line interface to both executables is as follows:

java -jar <PROGRAM>.jar [<CONFIGPATH>] [<INPUTPATH>]

where:

	<PROGRAM> - name of one of the programs,
	<CONFIGPATH> - path to the configuration file, ./config.properties when missing,
	<INPUTPATH> - path to the trace file, standard input when missing.

TraceExtender.jar extends the trace with the analytical solution to KiBaM.

BatteryLifeEstimator.jar performs battery life estimation for an input trace.