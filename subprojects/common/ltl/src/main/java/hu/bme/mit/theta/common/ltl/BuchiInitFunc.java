package hu.bme.mit.theta.common.ltl;

import hu.bme.mit.theta.analysis.InitFunc;
import hu.bme.mit.theta.analysis.Prec;
import hu.bme.mit.theta.analysis.unit.UnitState;
import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.CfaState;

import java.util.Collection;
import java.util.List;

public final class BuchiInitFunc<P extends Prec> implements InitFunc<CfaState<UnitState>, P> {

	private final CFA.Loc initLoc;

	private BuchiInitFunc(CFA.Loc initLoc) {
		this.initLoc = initLoc;
	}

	public static<P extends Prec> BuchiInitFunc<P> of(CFA.Loc initLoc) {
		return new BuchiInitFunc<>(initLoc);
	}

	@Override
	public Collection<CfaState<UnitState>> getInitStates(P prec) {
		return List.of(CfaState.of(initLoc, UnitState.getInstance()));
	}
}
