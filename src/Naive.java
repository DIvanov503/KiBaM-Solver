import java.util.List;

public class Naive {
	protected double fullCharge;
	
	protected double time = 0;
	
	public Naive(double fullCharge)
	{
		this.fullCharge = fullCharge;
	}
	
	public void updateCharge(double power, double time)
	{
		double dt = time - this.time;
		if (dt < 0){
			System.out.println("negative!");
			dt = 1/0;
		}
		if (Math.abs(dt) > 0)
		{
			this.time = time;
			fullCharge -= power * dt;
		}
	}
	
	public void updateCharge2(double power, double dt)
	{
		if (Math.abs(dt) > 0)
		{
			this.time += dt;
			fullCharge -= power * dt;
		}
	}
	
	public void updateChargeLinear(PolynomialCurrent current, double time)
	{
		for (List<Double> piece : current.current) {
			double dt = piece.get(0) > 0 ? piece.get(0) : time - this.time, i0 = piece.get(1);
			this.time += dt;
			if (piece.size() > 2) {
				double i1 = piece.get(1);
				fullCharge -= (i1 * dt / 2 + i0) * dt;
			} else {
				updateCharge2(i0, dt);			}
		}
		if (this.time > time) {
			System.err.println("The transition time between " + current.current.get(0).get(1) + " and " + current.current.get(current.current.size() - 1).get(1) + " power states exceed the target state duration. Please, provide a shorter trasition or fix the model.");
			System.exit(1);
		}
	}
	
	public double getFullCharge()
	{
		return fullCharge;
	}
	
	public void setFullCharge(double fullCharge)
	{
		this.fullCharge = fullCharge;
	}
	
	public void setTime(double time)
	{
		this.time = time;
	}
}
