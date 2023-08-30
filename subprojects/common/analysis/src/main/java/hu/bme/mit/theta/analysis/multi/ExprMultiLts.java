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

import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;

import java.util.function.BiFunction;
import java.util.function.Function;

public final class ExprMultiLts<LState extends ExprState, RState extends ExprState, DataState extends ExprState, LBlank extends ExprState, RBlank extends ExprState, LAction extends ExprAction, RAction extends ExprAction>
		extends MultiLts<LState, RState, DataState, LBlank, RBlank, LAction, RAction, ExprMultiState<LBlank, RBlank, DataState>, ExprMultiAction<LAction, RAction>> {

	private ExprMultiLts(Function<ExprMultiState<LBlank, RBlank, DataState>, MultiSourceSide> defineNextSide, Side<LState, DataState, LBlank, LAction> left, Side<RState, DataState, RBlank, RAction> right) {
		super(defineNextSide, left, right);
	}

	public static<LState extends ExprState, RState extends ExprState, DataState extends ExprState, LBlank extends ExprState, RBlank extends ExprState, LAction extends ExprAction, RAction extends ExprAction>
	ExprMultiLts<LState, RState, DataState, LBlank, RBlank, LAction, RAction> of(
			LTS<? super LState, LAction> leftLts, BiFunction<LBlank, DataState, LState> wrapLeftState,
			LTS<? super RState, RAction> rightLts, BiFunction<RBlank, DataState, RState> wrapRightState,
			Function<ExprMultiState<LBlank, RBlank, DataState>, MultiSourceSide> defineNextSide) {
		return new ExprMultiLts<>(defineNextSide, new Side<>(leftLts, wrapLeftState), new Side<>(rightLts, wrapRightState));
	}

	@Override
	ExprMultiAction<LAction, RAction> wrapLeftAction(LAction action) {
		return ExprMultiAction.ofLeftExprAction(action);
	}

	@Override
	ExprMultiAction<LAction, RAction> wrapRightAction(RAction action) {
		return ExprMultiAction.ofRightExprAction(action);
	}
}
