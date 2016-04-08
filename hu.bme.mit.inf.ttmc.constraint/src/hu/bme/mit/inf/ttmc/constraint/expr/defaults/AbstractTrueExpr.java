package hu.bme.mit.inf.ttmc.constraint.expr.defaults;

import hu.bme.mit.inf.ttmc.constraint.ConstraintManager;
import hu.bme.mit.inf.ttmc.constraint.expr.TrueExpr;
import hu.bme.mit.inf.ttmc.constraint.type.BoolType;
import hu.bme.mit.inf.ttmc.constraint.utils.ExprVisitor;

public abstract class AbstractTrueExpr extends AbstractBoolLitExpr implements TrueExpr {

	private static final int HASH_SEED = 242181;

	private static final String OPERATOR = "True";

	private final ConstraintManager manager;

	public AbstractTrueExpr(final ConstraintManager manager) {
		this.manager = manager;
	}

	@Override
	public final boolean getValue() {
		return true;
	}

	@Override
	public final BoolType getType() {
		return manager.getTypeFactory().Bool();
	}

	@Override
	public final <P, R> R accept(final ExprVisitor<? super P, ? extends R> visitor, final P param) {
		return visitor.visit(this, param);
	}

	@Override
	public final int hashCode() {
		return HASH_SEED;
	}

	@Override
	public final boolean equals(final Object obj) {
		return (obj instanceof TrueExpr);
	}

	@Override
	public final String toString() {
		return OPERATOR;
	}

}