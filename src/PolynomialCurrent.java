import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunctionLagrangeForm;
import org.apache.commons.math3.fitting.PolynomialCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

public class PolynomialCurrent extends Current {
	// [[t, i0, i1, ...], ...]
	
	PolynomialCurrent(double currentIntensity) throws IOException {
		List<Double> piece = new ArrayList<>();
		piece.add(0.0);
		piece.add(currentIntensity);
		current.add(piece);
	}
	
	PolynomialCurrent(String filename, int degree) throws IOException {
		current = new ArrayList<>();
		Reader in = new FileReader(filename);
		Iterable<CSVRecord> records = CSVFormat.RFC4180.parse(in);
		Iterator<CSVRecord> iterator = records.iterator();
		ArrayList<Double> x = new ArrayList<>(), y = new ArrayList<>();
		double x0 = 0, last_x = 0;
		List<Double> steady = null;
		while (iterator.hasNext()) {
			double xi, yi;
			CSVRecord record = iterator.next();
			
			try {
				xi = Double.parseDouble(record.get(0));
			} catch(NumberFormatException e) {
				// record is a piece delimiter
				if (x.size() > degree + 1) {
					// fit
					PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree);
					ArrayList<WeightedObservedPoint> points = new ArrayList<WeightedObservedPoint>();
					for (int i = 0; i < x.size(); ++i) {
						points.add(new WeightedObservedPoint(1.0, x.get(i), y.get(i)));
					}
					double[] coefficients = fitter.fit(points);
					List<Double> piece = new ArrayList<Double>();
					piece.add(x.get(x.size() - 1));
					for (int j = coefficients.length - 1; j >= 0; --j) {
						piece.add(coefficients[j]);
					}
					current.add(piece);
				} else if (x.size() > 1)  {
					// interpolate
					double[] xArray = new double[x.size()], yArray = new double[y.size()];
					for (int j = 0; j < x.size(); ++j) {
						xArray[j] = x.get(j);
						yArray[j] = y.get(j);
					}
					PolynomialFunctionLagrangeForm polynomial = new PolynomialFunctionLagrangeForm(xArray, yArray);
					double[] coefficients = polynomial.getCoefficients();
					List<Double> piece = new ArrayList<Double>();
					piece.add(x.get(x.size() - 1));
					for (double coefficient : coefficients) {
						piece.add(coefficient);
					}
					current.add(piece);
				}
				if (steady != null) {
					// accumulate the remaining time in the steady period
					steady.set(0, steady.get(0) - x.get(x.size() - 1));
				} else if (record.get(0).equals("steady")) {
					// create a steady piece
					steady = new ArrayList<Double>();
					steady.add(0.0);
					steady.add(y.get(y.size() - 1));
					current.add(steady);
					x.clear();
					y.clear();
					continue;
				}
				// the last point in the current (non-steady) piece is the first point in the next piece
				double last_y = y.get(y.size() - 1);
				x.clear();
				y.clear();
				x.add(0.0);
				y.add(last_y);
				x0 = last_x;
				continue;
			}
			yi = Double.parseDouble(record.get(1));
			if (x.isEmpty()) {
				// the current point is the first point in the current piece
				x0 = xi;
				x.add(0.0);
			} else {
				// time begins from 0 in a piece
				x.add(xi - x0);
			}
			y.add(yi);
			last_x = xi;
		}
		// approximate the rest of the points
		if (x.size() > 1) {
			if (x.size() > degree + 1) {
				// fit
				PolynomialCurveFitter fitter = PolynomialCurveFitter.create(degree);
				ArrayList<WeightedObservedPoint> points = new ArrayList<WeightedObservedPoint>();
				for (int i = 0; i < x.size(); ++i) {
					points.add(new WeightedObservedPoint(1.0, x.get(i), y.get(i)));
				}
				double[] coefficients = fitter.fit(points);
				List<Double> piece = new ArrayList<Double>();
				piece.add(x.get(x.size() - 1));
				for (int j = coefficients.length - 1; j >= 0; --j) {
					piece.add(coefficients[j]);
				}
				current.add(piece);
			} else if (x.size() > 1)  {
				// interpolate
				double[] xArray = new double[x.size()], yArray = new double[y.size()];
				for (int j = 0; j < x.size(); ++j) {
					xArray[j] = x.get(j);
					yArray[j] = y.get(j);
				}
				PolynomialFunctionLagrangeForm polynomial = new PolynomialFunctionLagrangeForm(xArray, yArray);
				double[] coefficients = polynomial.getCoefficients();
				List<Double> piece = new ArrayList<Double>();
				piece.add(x.get(x.size() - 1));
				for (double coefficient : coefficients) {
					piece.add(coefficient);
				}
				current.add(piece);
			}
			if (steady != null) {
				// accumulate the remaining time in the steady period
				steady.set(0, steady.get(0) - x.get(x.size() - 1));
			}
			
		}
		in.close();
	}
	
}
