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

import hu.bme.mit.theta.analysis.Action;
import hu.bme.mit.theta.analysis.LTS;
import hu.bme.mit.theta.analysis.State;

import java.util.function.BiFunction;
import java.util.function.Function;

@FunctionalInterface
public interface MultiLtsBuilderFunc<LState extends State, RState extends State, DataState extends State, LBlank extends State, RBlank extends State,
		LAction extends Action, RAction extends Action, MState extends MultiState<LBlank, RBlank, DataState>, MAction extends MultiAction<LAction, RAction>,
		MLts extends MultiLts<LState, RState, DataState, LBlank, RBlank, LAction, RAction, MState, MAction>> {

	MLts build(LTS<? super LState, LAction> leftLts, BiFunction<LBlank, DataState, LState> combineLeftState,
			   LTS<? super RState, RAction> rightLts, BiFunction<RBlank, DataState, RState> combineRightState,
			   Function<MState, MultiSourceSide> defineNextSide);

}
