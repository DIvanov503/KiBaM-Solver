import java.util.List;

import org.apache.commons.math3.util.CombinatoricsUtils;

public class Battery {
	// Permanent battery parameters
	protected double c, k;
	// Battery charge components
	protected double availableCharge, boundCharge;
	// Precomputed expressions
	protected double fullCharge;
	
	protected double time = 0;
	
	protected double eps = Double.MIN_VALUE;
	
	public Battery(double c, double k, double fullCharge)
	{
		this.c = c;
		this.k = k;
		this.availableCharge = c * fullCharge;
		this.boundCharge = (1 - c) * fullCharge;
		this.fullCharge = fullCharge;
	}
	
	public Battery(double c, double k, double availableCharge, double boundCharge)
	{
		this.c = c;
		this.k = k;
		this.availableCharge = availableCharge;
		this.boundCharge = boundCharge;
		this.fullCharge = availableCharge + boundCharge;
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
			// Reused expressions
			double kt = k * dt, e_kt = Math.exp(-kt);
			this.time = time;
			availableCharge = availableCharge * e_kt + ((fullCharge * k * c - power) * (1 - e_kt) - power * c * (kt - 1 + e_kt)) / k;
			boundCharge = boundCharge * e_kt + fullCharge * (1 - c) * (1 - e_kt) - power * (1 - c) * (kt - 1 + e_kt) / k;
			fullCharge = availableCharge + boundCharge;
		}
	}
	
	public void updateCharge2(double power, double dt)
	{
		if (Math.abs(dt) > 0)
		{
			// Reused expressions
			double kt = k * dt, e_kt = Math.exp(-kt);
			this.time += dt;
			availableCharge = availableCharge * e_kt + ((fullCharge * k * c - power) * (1 - e_kt) - power * c * (kt - 1 + e_kt)) / k;
			boundCharge = boundCharge * e_kt + fullCharge * (1 - c) * (1 - e_kt) - power * (1 - c) * (kt - 1 + e_kt) / k;
			fullCharge = availableCharge + boundCharge;
		}
	}
	
	public void updateChargePolynomial(Current current, double time)
	{
		for (List<Double> piece : current.current) {
			double dt, kt, e_kt;
			if (piece.get(0) > 0) {
				dt = piece.get(0);
				this.time += dt;
			} else {
				dt = time - this.time + piece.get(0);
				this.time = time + piece.get(0);
			}
			kt = k * dt;
			e_kt = Math.exp(-kt);
			availableCharge = availableCharge * e_kt + fullCharge * c * (1 - e_kt);
			double sum = 0, dt_j = dt;
			for (int j = 1; j < piece.size(); ++j) {
				sum += piece.get(j) * dt_j / j;
				dt_j *= dt;
			}
			availableCharge -= sum;
			sum = 0;
			double sign_j = 1.0, k_j = k;
			long fact_j = 1;
			for (int j = 1; j < piece.size(); ++j) {
				double k_l = 1.0, sign_l = 1.0;
				double subsum = sign_j * e_kt / k_j;
				for (int l = 0; l <= j; ++l) {
					subsum += sign_l * Math.pow(dt, j - l) / k_l / CombinatoricsUtils.factorial(j - l);
					k_l *= k;
					sign_l = -sign_l;
				}
				k_j *= k;
				sign_j = -sign_j;
				sum += piece.get(j) * fact_j * subsum;
				fact_j *= j;
			}
			sum *= 1 - c;
			availableCharge += sum;
			boundCharge = boundCharge * e_kt + fullCharge * (1 - c) * (1 - e_kt);
			boundCharge -= sum;
			fullCharge = availableCharge + boundCharge;
		}
	}
	
	public void updateChargeTrigonometric(Current current, double time)
	{
		for (List<Double> piece : current.current) {
			double dt, kt, e_kt;
			if (piece.get(0) > 0) {
				dt = piece.get(0);
				this.time += dt;
			} else {
				dt = time - this.time + piece.get(0);
				this.time = time + piece.get(0);
			}
			kt = k * dt;
			e_kt = Math.exp(-kt);
			availableCharge = availableCharge * e_kt + fullCharge * c * (1 - e_kt);
			if (piece.size() > 1) {
				availableCharge -= dt * piece.get(1);
			}
			double sum = 0, pi_L = Math.PI / dt;
			for (int j = 1, l = 2; l < piece.size(); ++j, l += 2) {
				double jpi_L = j * pi_L;
				sum += (piece.get(l) * Math.sin(jpi_L * dt) + piece.get(l + 1) * (1 - Math.cos(jpi_L * dt))) / jpi_L;
			}
			availableCharge -= sum;
			if (piece.size() > 1) {
				sum = piece.get(1) * (kt - 1 + e_kt) / k;
			} else {
				sum = 0;
			}
			double k2 = k * k, ke_kt = k * e_kt;
			for (int j = 1, l = 2; l < piece.size(); ++j, l += 2) {
				double jpi_L = j * pi_L, jpi_L2 = jpi_L * jpi_L;
				sum += piece.get(l) * (Math.sin(jpi_L * dt) / jpi_L - (k * Math.cos(jpi_L * dt) + jpi_L * Math.sin(jpi_L * dt) - ke_kt) / (jpi_L2 + k2)) + piece.get(l + 1) * ((1 - Math.cos(jpi_L * dt)) / jpi_L - (k * Math.sin(jpi_L * dt) - jpi_L * Math.cos(jpi_L * dt) + ke_kt) / (jpi_L2 + k2));
			}
			sum *= 1 - c;
			availableCharge += sum;
			boundCharge = boundCharge * e_kt + fullCharge * (1 - c) * (1 - e_kt);
			boundCharge -= sum;
			fullCharge = availableCharge + boundCharge;
		}
	}
	
	public double getAvailableCharge()
	{
		return availableCharge;
	}

	public double getBoundCharge()
	{
		return boundCharge;
	}
	
	public double getFullCharge()
	{
		return fullCharge;
	}
	
	public void setAvailableCharge(double availableCharge)
	{
		this.availableCharge = availableCharge;
		this.fullCharge = availableCharge + boundCharge;
	}

	public void setBoundCharge(double boundCharge)
	{
		this.boundCharge = boundCharge;
		this.fullCharge = availableCharge + boundCharge;
	}
	
	public void setFullCharge(double fullCharge)
	{
		this.fullCharge = fullCharge;
		this.availableCharge = c * fullCharge;
		this.boundCharge = (1 - c) * fullCharge;
	}
	
	public void setC(double c)
	{
		this.c = c;
	}
	
	public void setK(double k)
	{
		this.k = k;
	}
	
	public void setTime(double time)
	{
		this.time = time;
	}
}
