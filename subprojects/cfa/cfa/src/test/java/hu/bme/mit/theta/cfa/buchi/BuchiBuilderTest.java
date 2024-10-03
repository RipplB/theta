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
package hu.bme.mit.theta.cfa.buchi;

import hu.bme.mit.theta.cfa.CFA;
import hu.bme.mit.theta.cfa.analysis.utils.CfaVisualizer;
import hu.bme.mit.theta.common.logging.ConsoleLogger;
import hu.bme.mit.theta.common.logging.Logger;
import hu.bme.mit.theta.common.visualization.writer.GraphvizWriter;
import hu.bme.mit.theta.core.decl.VarDecl;
import hu.bme.mit.theta.core.type.booltype.BoolType;
import jhoafparser.consumer.HOAConsumerException;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

import static hu.bme.mit.theta.core.decl.Decls.Var;

public class BuchiBuilderTest {

	private static final Logger.Level LOGLEVEL = Logger.Level.VERBOSE;

	@Test
	public void testSimpleExpression() throws HOAConsumerException {
		Logger logger = new ConsoleLogger(LOGLEVEL);
		String ltlExpression = "not G(F(p))";
		VarDecl<BoolType> p = Var("p", BoolType.getInstance());
		CFA cfa = BuchiBuilder.of(ltlExpression, List.of(p), logger);
		logger.write(Logger.Level.VERBOSE, GraphvizWriter.getInstance().writeString(CfaVisualizer.visualize(cfa)));
		Assert.assertEquals(1, cfa.getLocs().size());
		Assert.assertEquals(2, cfa.getEdges().size());
		Assert.assertEquals(1, cfa.getAcceptingEdges().size());
	}

}
