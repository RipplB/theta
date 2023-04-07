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

import hu.bme.mit.theta.core.decl.Decl;
import hu.bme.mit.theta.core.decl.IndexedConstDecl;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.model.Valuation;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.type.anytype.RefExpr;
import hu.bme.mit.theta.core.utils.indexings.VarIndexing;

import static hu.bme.mit.theta.core.type.anytype.Exprs.Prime;

public final class ItpFolder {

	private final VarIndexing indexing;
	private final Valuation valuation;

	public ItpFolder(final VarIndexing indexing, Valuation valuation) {
		this.indexing = indexing;
		this.valuation = valuation;
	}

	public <T extends Type> Expr<T> foldIn(final Expr<T> expr) {
		if (expr instanceof RefExpr<T> ref) {
			final Decl<T> decl = ref.getDecl();
			if (decl instanceof IndexedConstDecl<T> constDecl) {
				final VarDecl<T> varDecl = constDecl.getVarDecl();
				final int index = constDecl.getIndex();
				final int nPrimes = index - indexing.get(varDecl);
				final Expr<T> varRef = varDecl.getRef();
				if (nPrimes == 0)
					return varRef;
				if (nPrimes > 0)
					return Prime(varRef, nPrimes);
				return expr.eval(valuation);
			}
		}

		return expr.map(this::foldIn);
	}
}
