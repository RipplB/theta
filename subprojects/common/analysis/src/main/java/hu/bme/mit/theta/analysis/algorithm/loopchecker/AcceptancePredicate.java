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
package hu.bme.mit.theta.analysis.algorithm.loopchecker;

import hu.bme.mit.theta.analysis.expr.ExprAction;
import hu.bme.mit.theta.analysis.expr.ExprState;

import java.util.function.Predicate;

public final class AcceptancePredicate<S extends ExprState, A extends ExprAction> {

	private final Predicate<? super S> statePredicate;
	private final Predicate<? super A> actionPredicate;

	private AcceptancePredicate(Predicate<? super S> statePredicate, Predicate<? super A> actionPredicate) {
		this.statePredicate = statePredicate;
		this.actionPredicate = actionPredicate;
	}

	public static <S extends ExprState, A extends ExprAction> AcceptancePredicate<S, A> ofStatePredicate(Predicate<? super S> statePredicate) {
		return new AcceptancePredicate<>(statePredicate, null);
	}

	public static <S extends ExprState, A extends ExprAction> AcceptancePredicate<S, A> ofActionPredicate(Predicate<? super A> actionPredicate) {
		return new AcceptancePredicate<>(null, actionPredicate);
	}

	public static <S extends ExprState, A extends ExprAction> AcceptancePredicate<S, A> ofCombinedPredicate(Predicate<? super S> statePredicate, Predicate<? super A> actionPredicate) {
		return new AcceptancePredicate<>(statePredicate, actionPredicate);
	}

	public static <S extends ExprState, A extends ExprAction> AcceptancePredicate<S, A> alwaysTrue() {
		return new AcceptancePredicate<>(null, null);
	}

	public boolean test(S state, A action) {
		return (statePredicate == null || statePredicate.test(state)) && (actionPredicate == null || actionPredicate.test(action));
	}

	public boolean testState(S state) {
		if (statePredicate == null || state == null)
			return false;
		return statePredicate.test(state);
	}

	public boolean testAction(A action) {
		if (actionPredicate == null || action == null)
			return false;
		return actionPredicate.test(action);
	}

}
