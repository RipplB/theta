package hu.bme.mit.theta.analysis.algorithm.loopchecker;

public enum RefinerStrategy {
	MILANO, BOUNDED_UNROLLING;

	public static RefinerStrategy defaultValue() {
		return RefinerStrategy.MILANO;
	}
}
