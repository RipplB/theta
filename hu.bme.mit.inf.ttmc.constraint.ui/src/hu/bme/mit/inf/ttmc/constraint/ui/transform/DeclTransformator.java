package hu.bme.mit.inf.ttmc.constraint.ui.transform;

import hu.bme.mit.inf.ttmc.constraint.decl.Decl;
import hu.bme.mit.inf.ttmc.constraint.model.Declaration;
import hu.bme.mit.inf.ttmc.constraint.type.Type;

public interface DeclTransformator {

	public Decl<? extends Type, ?> transform(Declaration declaration);

}