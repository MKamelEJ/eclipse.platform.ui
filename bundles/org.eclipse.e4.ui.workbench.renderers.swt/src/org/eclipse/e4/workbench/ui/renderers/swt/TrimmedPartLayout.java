/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.workbench.ui.renderers.swt;

import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Layout;

/**
 * This arranges its controls into 5 'slots' defined by its composite children
 * <ol>
 * <li>Top: spans the entire width and abuts the top of the container</li>
 * <li>Bottom: spans the entire width and abuts the bottom of the container</li>
 * <li>Left: spans the space between 'top' and 'bottom' and abuts the left of
 * the container</li>
 * <li>Right: spans the space between 'top' and 'bottom' and abuts the right of
 * the container</li>
 * <li>Center: fills the area remaining once the other controls have been
 * positioned</li>
 * </ol>
 * 
 * <strong>NOTE:</strong> <i>All</i> the child controls must exist. Also,
 * computeSize is not implemented because we expect this to be used in
 * situations (i.e. shells) where the outer bounds are always 'set', not
 * computed. Also, the interior structure of the center may contain overlapping
 * controls so it may not be capable of performing the calculation.
 * 
 * @author emoffatt
 * 
 */
public class TrimmedPartLayout extends Layout {
	public Composite top;
	public Composite bottom;
	public Composite left;
	public Composite right;
	public Composite clientArea;

	/**
	 * This layout is used to support parts that want trim for their containing
	 * composites.
	 * 
	 * @param trimOwner
	 */
	public TrimmedPartLayout(Composite parent) {
		clientArea = new Composite(parent, SWT.NONE);
		clientArea.setLayout(new FillLayout());
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * org.eclipse.swt.widgets.Layout#computeSize(org.eclipse.swt.widgets.Composite
	 * , int, int, boolean)
	 */
	protected Point computeSize(Composite composite, int wHint, int hHint,
			boolean flushCache) {
		// We can't actually compute a size so return a default
		return new Point(SWT.DEFAULT, SWT.DEFAULT);
	}

	protected void layout(Composite composite, boolean flushCache) {
		if (composite.isDisposed() || clientArea.isDisposed()) {
			return;
		}

		composite.setRedraw(false);
		try {
			Rectangle ca = composite.getClientArea();
			Rectangle caRect = new Rectangle(ca.x, ca.y, ca.width, ca.height);

			// 'Top' spans the entire area
			if (top != null) {
				Point topSize = top
						.computeSize(caRect.width, SWT.DEFAULT, true);
				caRect.y += topSize.y;
				caRect.height -= topSize.y;

				// Don't layout unless we've changed
				Rectangle newBounds = new Rectangle(ca.x, ca.y, caRect.width,
						topSize.y);
				if (!newBounds.equals(top.getBounds())) {
					top.setBounds(newBounds);
				}
			}

			// 'Bottom' spans the entire area
			if (bottom != null) {
				Point bottomSize = bottom.computeSize(caRect.width,
						SWT.DEFAULT, true);
				caRect.height -= bottomSize.y;

				// Don't layout unless we've changed
				Rectangle newBounds = new Rectangle(caRect.x, caRect.y
						+ caRect.height, caRect.width, bottomSize.y);
				if (!newBounds.equals(bottom.getBounds())) {
					bottom.setBounds(newBounds);
				}
			}

			// 'Left' spans between 'top' and 'bottom'
			if (left != null) {
				Point leftSize = left.computeSize(SWT.DEFAULT, caRect.height,
						true);
				caRect.x += leftSize.x;
				caRect.width -= leftSize.x;

				// Don't layout unless we've changed
				Rectangle newBounds = new Rectangle(caRect.x - leftSize.x,
						caRect.y, leftSize.x, caRect.height);
				if (!newBounds.equals(left.getBounds())) {
					left.setBounds(newBounds);
				}
			}

			// 'Right' spans between 'top' and 'bottom'
			if (right != null) {
				Point rightSize = right.computeSize(SWT.DEFAULT, caRect.height,
						true);
				caRect.width -= rightSize.x;

				// Don't layout unless we've changed
				Rectangle newBounds = new Rectangle(caRect.x + caRect.width,
						caRect.y, rightSize.x, caRect.height);
				if (!newBounds.equals(right.getBounds())) {
					right.setBounds(newBounds);
				}
			}

			// Don't layout unless we've changed
			if (!caRect.equals(clientArea.getBounds())) {
				clientArea.setBounds(caRect);
			}
		} finally {
			composite.setRedraw(true);
		}
	}

	/**
	 * @param top2
	 * @param b
	 * @return
	 */
	public Composite getTrimComposite(Composite parent, int side) {
		if (side == SWT.TOP) {
			if (top == null) {
				top = new Composite(parent, SWT.NONE);
				top.setLayout(new RowLayout(SWT.HORIZONTAL));
			}
			return top;
		} else if (side == SWT.BOTTOM) {
			if (bottom == null) {
				bottom = new Composite(parent, SWT.NONE);
				bottom.setLayout(new RowLayout(SWT.HORIZONTAL));
			}
			return bottom;
		} else if (side == SWT.LEFT) {
			if (left == null) {
				left = new Composite(parent, SWT.NONE);
				left.setLayout(new RowLayout(SWT.VERTICAL));
			}
			return left;
		} else if (side == SWT.RIGHT) {
			if (right == null) {
				right = new Composite(parent, SWT.NONE);
				right.setLayout(new RowLayout(SWT.VERTICAL));
			}
			return right;
		}

		// Unknown location
		return null;
	}

}
