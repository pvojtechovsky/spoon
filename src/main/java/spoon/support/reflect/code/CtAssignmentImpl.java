/**
 * Copyright (C) 2006-2018 INRIA and contributors
 * Spoon - http://spoon.gforge.inria.fr/
 *
 * This software is governed by the CeCILL-C License under French law and
 * abiding by the rules of distribution of free software. You can use, modify
 * and/or redistribute the software under the terms of the CeCILL-C license as
 * circulated by CEA, CNRS and INRIA at http://www.cecill.info.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the CeCILL-C License for more details.
 *
 * The fact that you are presently reading this means that you have had
 * knowledge of the CeCILL-C license and that you accept its terms.
 */
package spoon.support.reflect.code;


import java.util.ArrayList;
import java.util.List;
import spoon.reflect.annotations.MetamodelPropertyField;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtExpression;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.CtVisitor;
import spoon.support.reflect.declaration.CtElementImpl;

import static spoon.reflect.ModelElementContainerDefaultCapacities.CASTS_CONTAINER_DEFAULT_CAPACITY;
import static spoon.reflect.path.CtRole.ASSIGNED;
import static spoon.reflect.path.CtRole.ASSIGNMENT;
import static spoon.reflect.path.CtRole.CAST;
import static spoon.reflect.path.CtRole.TYPE;




public class CtAssignmentImpl<T, A extends T> extends CtStatementImpl implements CtAssignment<T, A> {
	private static final long serialVersionUID = 1L;

	@MetamodelPropertyField(role = ASSIGNED)
	CtExpression<T> assigned;

	@MetamodelPropertyField(role = ASSIGNMENT)
	CtExpression<A> assignment;

	@MetamodelPropertyField(role = TYPE)
	CtTypeReference<T> type;

	@MetamodelPropertyField(role = CAST)
	List<CtTypeReference<?>> typeCasts = emptyList();

	@Override
	public void accept(CtVisitor visitor) {
		visitor.visitCtAssignment(this);
	}

	@Override
	public CtExpression<T> getAssigned() {
		return assigned;
	}

	@Override
	public CtExpression<A> getAssignment() {
		return assignment;
	}

	@Override
	public CtTypeReference<T> getType() {
		return type;
	}

	@Override
	public List<CtTypeReference<?>> getTypeCasts() {
		return typeCasts;
	}

	@Override
	public CtAssignmentImpl<T, A> setAssigned(CtExpression<T> assigned) {
		if (assigned != null) {
			assigned.setParent(this);
		}
		getFactory().getEnvironment().getModelChangeListener().onObjectUpdate(this, ASSIGNED, assigned, this.assigned);
		this.assigned = assigned;
		return this;
	}

	@Override
	public CtAssignmentImpl<T, A> setAssignment(CtExpression<A> assignment) {
		if (assignment != null) {
			assignment.setParent(this);
		}
		getFactory().getEnvironment().getModelChangeListener().onObjectUpdate(this, ASSIGNMENT, assignment, this.assignment);
		this.assignment = assignment;
		return this;
	}

	@Override
	public CtAssignmentImpl<T, A> setType(CtTypeReference<T> type) {
		if (type != null) {
			type.setParent(this);
		}
		getFactory().getEnvironment().getModelChangeListener().onObjectUpdate(this, TYPE, type, this.type);
		this.type = type;
		return this;
	}

	@Override
	public CtAssignmentImpl<T, A> setTypeCasts(List<CtTypeReference<?>> casts) {
		if (casts == null || casts.isEmpty()) {
			this.typeCasts = CtElementImpl.emptyList();
			return this;
		}
		if (this.typeCasts == CtElementImpl.<CtTypeReference<?>>emptyList()) {
			this.typeCasts = new ArrayList<>(CASTS_CONTAINER_DEFAULT_CAPACITY);
		}
		getFactory().getEnvironment().getModelChangeListener().onListDeleteAll(this, CAST, this.typeCasts, new ArrayList<>(this.typeCasts));
		this.typeCasts.clear();
		for (CtTypeReference<?> cast : casts) {
			addTypeCast(cast);
		}
		return this;
	}

	@Override
	public CtAssignmentImpl<T, A> addTypeCast(CtTypeReference<?> type) {
		if (type == null) {
			return this;
		}
		if (typeCasts == CtElementImpl.<CtTypeReference<?>>emptyList()) {
			typeCasts = new ArrayList<>(CASTS_CONTAINER_DEFAULT_CAPACITY);
		}
		type.setParent(this);
		getFactory().getEnvironment().getModelChangeListener().onListAdd(this, CAST, typeCasts, type);
		typeCasts.add(type);
		return this;
	}

	@Override
	public T S() {
		return null;
	}

	@Override
	public CtAssignment<T, A> clone() {
		return (CtAssignment<T, A>) super.clone();
	}
}
