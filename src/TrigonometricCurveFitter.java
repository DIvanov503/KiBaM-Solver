import java.util.Collection;

import org.apache.commons.math3.analysis.ParametricUnivariateFunction;
import org.apache.commons.math3.fitting.AbstractCurveFitter;
import org.apache.commons.math3.fitting.WeightedObservedPoint;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresBuilder;
import org.apache.commons.math3.fitting.leastsquares.LeastSquaresProblem;
import org.apache.commons.math3.linear.DiagonalMatrix;


public class TrigonometricCurveFitter extends AbstractCurveFitter {
	protected class TrigonometricSum implements ParametricUnivariateFunction {
		public double L;
		
		TrigonometricSum(double L) {
			this.L = L;
		}
		
		@Override
		public double[] gradient(double arg0, double... arg1) {
			double[] ans = new double[arg1.length];
			ans[0] = 1;
			for (int i = 1, j = 1; j < ans.length; ++i, j += 2) {
				ans[j] = Math.cos(Math.PI * i * arg0 / L);
			}
			for (int i = 1, j = 2; j < ans.length; ++i, j += 2) {
				ans[j] = Math.sin(Math.PI * i * arg0 / L);
			}
			return ans;
		}

		@Override
		public double value(double arg0, double... arg1) {
			double ans = arg1.length > 0 ? arg1[0] : 0;
			for (int i = 1, j = 1; j < arg1.length; ++i, j += 2) {
				ans += arg1[j] * Math.cos(Math.PI * i * arg0 / L) + arg1[j + 1] * Math.sin(Math.PI * i * arg0 / L);
			}
			return ans;
		}
	}
	/** Parametric function to be fitted. */
	private final ParametricUnivariateFunction FUNCTION;
	/** Initial guess. */
	private final double[] initialGuess;
	/** Maximum number of iterations of the optimization algorithm. */
	private final int maxIter;
	
	
	private TrigonometricCurveFitter(double[] initialGuess, double L, int maxIter) {
		this.initialGuess = initialGuess;
		this.FUNCTION = new TrigonometricSum(L);
		this.maxIter = maxIter;
	}
		
	public static TrigonometricCurveFitter create(int degree, double L) {
		return new TrigonometricCurveFitter(new double[2 * degree + 1], L, Integer.MAX_VALUE);
	}
		

	@Override
	protected LeastSquaresProblem getProblem(Collection<WeightedObservedPoint> observations) {
		// Prepare least-squares problem.
		final int len = observations.size();
		final double[] target  = new double[len];
		final double[] weights = new double[len];
		int i = 0;
		for (WeightedObservedPoint obs : observations) {
			target[i]  = obs.getY();
			weights[i] = obs.getWeight();
			++i;
		}
			
			
		final AbstractCurveFitter.TheoreticalValuesFunction model =
                new AbstractCurveFitter.TheoreticalValuesFunction(FUNCTION, observations);
        // Return a new least squares problem set up to fit a polynomial curve to the
        // observed points.
		return new LeastSquaresBuilder().
				maxEvaluations(Integer.MAX_VALUE).
				maxIterations(maxIter).
				start(initialGuess).
				target(target).
				weight(new DiagonalMatrix(weights)).
				model(model.getModelFunction(), model.getModelFunctionJacobian()).
				build();
	}
			
}