/*
 *  Copyright 2024 Budapest University of Technology and Economics
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
package hu.bme.mit.theta.solver.javasmt;

import com.google.common.collect.Sets;
import hu.bme.mit.theta.common.OsHelper;
import hu.bme.mit.theta.common.OsHelper.OperatingSystem;
import hu.bme.mit.theta.core.type.Expr;
import hu.bme.mit.theta.core.type.Type;
import hu.bme.mit.theta.core.type.bvtype.BvRotateLeftExpr;
import hu.bme.mit.theta.core.type.bvtype.BvRotateRightExpr;
import hu.bme.mit.theta.core.type.bvtype.BvSModExpr;
import hu.bme.mit.theta.core.type.fptype.FpRemExpr;
import hu.bme.mit.theta.core.type.fptype.FpType;
import hu.bme.mit.theta.core.type.rattype.RatType;
import hu.bme.mit.theta.core.utils.ArrayTestUtils;
import hu.bme.mit.theta.core.utils.BoolTestUtils;
import hu.bme.mit.theta.core.utils.BvTestUtils;
import hu.bme.mit.theta.core.utils.FpTestUtils;
import hu.bme.mit.theta.core.utils.IntTestUtils;
import hu.bme.mit.theta.core.utils.RatTestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.sosy_lab.common.ShutdownManager;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.log.BasicLogManager;
import org.sosy_lab.java_smt.SolverContextFactory;
import org.sosy_lab.java_smt.SolverContextFactory.Solvers;
import org.sosy_lab.java_smt.api.SolverContext;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static hu.bme.mit.theta.core.type.abstracttype.AbstractExprs.Eq;
import static org.junit.Assert.assertNotNull;
import static org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaSMTTransformerTest {

    @Parameter(0)
    public Expr<?> expr;

    @Parameter(1)
    public Solvers solver;

    @Parameters(name = "expr: {0}, solver: {1}")
    public static Collection<?> operations() {
        final Set<Solvers> solvers;
        if (OsHelper.getOs().equals(OperatingSystem.LINUX)) {
            solvers = Set.of(Solvers.Z3, Solvers.CVC5, Solvers.PRINCESS);
        } else {
            solvers = Set.of(Solvers.Z3, Solvers.PRINCESS);
        }

        return Sets.cartesianProduct(Stream.of(
                                BvTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[2]),
                                BvTestUtils.BitvectorOperations().stream().map(o -> ((Object[]) o)[2]),
                                BvTestUtils.RelationalOperations().stream().map(o -> ((Object[]) o)[2]),
                                FpTestUtils.GetOperations().map(o -> ((Object[]) o)[2]),
                                IntTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[2]),
                                RatTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[2]),
                                BoolTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[2]),
                                ArrayTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[2]),


                                BvTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[1]),
                                BvTestUtils.BitvectorOperations().stream().map(o -> ((Object[]) o)[1]),
                                BvTestUtils.RelationalOperations().stream().map(o -> ((Object[]) o)[1]),
                                FpTestUtils.GetOperations().map(o -> ((Object[]) o)[1]),
                                IntTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[1]),
                                RatTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[1]),
                                BoolTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[1]),
                                ArrayTestUtils.BasicOperations().stream().map(o -> ((Object[]) o)[1])
                        ).reduce(Stream::concat).get()
                        .filter(JavaSMTTransformerTest::supported)
                        .collect(Collectors.toSet()), solvers).stream()
                .map(objects -> new Object[]{objects.get(0), objects.get(1)}).toList();
    }

    static boolean supported(Object o) {
        return !(o instanceof BvRotateLeftExpr) &&
                !(o instanceof BvRotateRightExpr) &&
                !(o instanceof FpRemExpr) &&
                !(o instanceof BvSModExpr) &&
                (!(o instanceof Expr<?>) || ((Expr<?>) o).getOps().stream().allMatch(JavaSMTTransformerTest::supported));
    }

    private static boolean hasType(Expr<?> expr, Predicate<Type> pred) {
        if (pred.test(expr.getType())) return true;
        return expr.getOps().stream().anyMatch((op) -> hasType(op, pred));
    }

    @Test
    public void testRoundtripTransformer() throws Exception {
        // Sanity check
        assertNotNull(expr);
        if (solver == Solvers.CVC5 && hasType(expr, type -> type instanceof FpType && !Set.of(32, 64).contains(((FpType) type).getSignificand() + ((FpType) type).getExponent()))) {
            return;
        }
        if (solver == Solvers.PRINCESS && hasType(expr, type -> type instanceof FpType || type instanceof RatType)) {
            return;
        }

        final JavaSMTSymbolTable javaSMTSymbolTable = new JavaSMTSymbolTable();
        final var config = Configuration.fromCmdLineArguments(new String[]{});
        final var logger = BasicLogManager.create(config);
        final var shutdownManager = ShutdownManager.create();
        try (final SolverContext context = SolverContextFactory.createSolverContext(config, logger, shutdownManager.getNotifier(), Solvers.Z3)) {
            final JavaSMTTransformationManager javaSMTExprTransformer = new JavaSMTTransformationManager(javaSMTSymbolTable, context);
            final JavaSMTTermTransformer javaSMTTermTransformer = new JavaSMTTermTransformer(javaSMTSymbolTable, context);

            final var expTerm = javaSMTExprTransformer.toTerm(expr);
            final var expExpr = javaSMTTermTransformer.toExpr(expTerm);

            try {
                Assert.assertEquals(expr, expExpr);
            } catch (AssertionError e) {
                try (final var solver = JavaSMTSolverFactory.create(this.solver, new String[]{}).createSolver()) {
                    solver.add(Eq(expr, expExpr));
                    Assert.assertTrue(solver.check().isSat());
                }
            }
        }

    }
}