/*
 *  Copyright 2023 Budapest University of Technology and Economics
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package hu.bme.mit.theta.analysis.multi;

import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import hu.bme.mit.theta.core.utils.indexings.VarIndexing;

public final class ExprMultiAction<L extends ExprAction, R extends ExprAction> extends MultiAction<L, R> implements ExprAction {
	private ExprMultiAction(L lAction, R rAction) {
		super(lAction, rAction);
	}

	public static<L extends ExprAction, R extends ExprAction> ExprMultiAction<L, R> ofLeftExprAction(L action) {
		return new ExprMultiAction<>(action, null);
	}

	public static<L extends ExprAction, R extends ExprAction> ExprMultiAction<L, R> ofRightExprAction(R action) {
		return new ExprMultiAction<>(null, action);
	}

	@Override
	public Expr<BoolType> toExpr() {
		return getLeftAction() == null ? getRightAction().toExpr() : getLeftAction().toExpr();
	}

	@Override
	public VarIndexing nextIndexing() {
		return getLeftAction() == null ? getRightAction().nextIndexing() : getLeftAction().nextIndexing();
	}

	@Override
	public String toString() {
		return "ExprMultiAction{" +
				"leftAction=" + leftAction +
				", rightAction=" + rightAction +
				'}';
	}
}