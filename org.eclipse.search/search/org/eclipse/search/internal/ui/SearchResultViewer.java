/*
 * (c) Copyright IBM Corp. 2000, 2001.
 * All Rights Reserved.
 */
package org.eclipse.search.internal.ui;


import java.util.ArrayList;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.core.runtime.IPath;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Item;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Widget;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.util.Assert;
import org.eclipse.jface.viewers.IBaseLabelProvider;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IOpenListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProviderChangedEvent;
import org.eclipse.jface.viewers.OpenEvent;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;

import org.eclipse.ui.IActionBars;
import org.eclipse.ui.IMemento;
import org.eclipse.ui.IWorkbenchActionConstants;
import org.eclipse.ui.actions.ActionContext;
import org.eclipse.ui.actions.ActionGroup;

import org.eclipse.search.ui.IActionGroupFactory;
import org.eclipse.search.ui.IContextMenuConstants;
import org.eclipse.search.ui.IContextMenuContributor;
import org.eclipse.search.ui.ISearchResultViewEntry;
import org.eclipse.search.ui.SearchUI;

import org.eclipse.search.internal.ui.util.FileLabelProvider;


/**
 * A special viewer to present search results. The viewer implements an
 * optimized adding and removing strategy. Furthermore it manages
 * contributions for search result types. For example the viewer's context
 * menu differs if the search result has been generated by a text or
 * a java search.
 */
public class SearchResultViewer extends TableViewer {
	
	private SearchResultView fOuterPart;
	private boolean fFirstTime= true;
	private ShowNextResultAction fShowNextResultAction;
	private ShowPreviousResultAction fShowPreviousResultAction;
	private GotoMarkerAction fGotoMarkerActionProxy;
	private SearchAgainAction fSearchAgainAction;
	private RemoveResultAction fRemoveSelectedMatchesAction;
	private SortDropDownAction fSortDropDownAction;
	private SearchDropDownAction fSearchDropDownAction;
	private CopyToClipboardAction fCopyToClipboardAction;
	private int fMarkerToShow;
	private boolean fHandleSelectionChangedEvents= true;
	private ISelection fLastSelection;
	private boolean fCurrentMatchRemoved= false;
	private Color fPotentialMatchFgColor;
	private ActionGroup fActionGroup;
	private IContextMenuContributor fContextMenuContributor;
	private IActionGroupFactory fActionGroupFactory;	
	private IAction fGotoMarkerAction;
	
	private ResourceToItemsMapper fResourceToItemsMapper;
	
	public SearchResultViewer(SearchResultView outerPart, Composite parent) {
		super(new Table(parent, SWT.MULTI | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION));

		fResourceToItemsMapper= new ResourceToItemsMapper(this);
		
		fOuterPart= outerPart;
		Assert.isNotNull(fOuterPart);

		if (SearchPreferencePage.arePotentialMatchesEmphasized())
			fPotentialMatchFgColor= new Color(SearchPlugin.getActiveWorkbenchShell().getDisplay(), SearchPreferencePage.getPotentialMatchForegroundColor());
		
		setUseHashlookup(true);
		setContentProvider(new SearchResultContentProvider());

		ILabelProvider labelProvider= new SearchResultLabelProvider(new FileLabelProvider(FileLabelProvider.SHOW_LABEL));
		setLabelProvider(labelProvider);
		
		boolean hasSearch= SearchManager.getDefault().getCurrentSearch() != null;


		fShowNextResultAction= new ShowNextResultAction(this);
		fShowNextResultAction.setEnabled(false);
		fShowPreviousResultAction= new ShowPreviousResultAction(this);
		fShowPreviousResultAction.setEnabled(false);
		fGotoMarkerActionProxy= new GotoMarkerAction(this);
		fGotoMarkerActionProxy.setEnabled(false);
		fRemoveSelectedMatchesAction= new RemoveResultAction(this, false);
		fRemoveSelectedMatchesAction.setEnabled(false);
		fSearchAgainAction= new SearchAgainAction();
		fSearchAgainAction.setEnabled(hasSearch);
		fSortDropDownAction = new SortDropDownAction(this);
		fSortDropDownAction.setEnabled(getItemCount() > 0);
		fSearchDropDownAction= new SearchDropDownAction(this);
		fSearchDropDownAction.setEnabled(hasSearch);
		fCopyToClipboardAction= new CopyToClipboardAction(this);

		addSelectionChangedListener(
			new ISelectionChangedListener() {
				public void selectionChanged(SelectionChangedEvent event) {
					if (fLastSelection == null || !fLastSelection.equals(event.getSelection())) {
						fLastSelection= event.getSelection();
						handleSelectionChanged();
					}
				}
			}
		);

		addOpenListener(new IOpenListener() {
			public void open(OpenEvent event) {
				showResult();
			}
		});
		
		MenuManager menuMgr= new MenuManager("#PopUp"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(
			new IMenuListener() {
				public void menuAboutToShow(IMenuManager mgr) {
					SearchPlugin.createStandardGroups(mgr);
					fillContextMenu(mgr);
				}
			});
		Menu menu= menuMgr.createContextMenu(getTable());
		getTable().setMenu(menu);		
		
		// Register menu
		fOuterPart.getSite().registerContextMenu(menuMgr, this);
		
		IActionBars actionBars= fOuterPart.getViewSite().getActionBars();
		if (actionBars != null) {
			actionBars.setGlobalActionHandler(IWorkbenchActionConstants.NEXT, fShowNextResultAction);
			actionBars.setGlobalActionHandler(IWorkbenchActionConstants.PREVIOUS, fShowPreviousResultAction);
		}

		fOuterPart.getSite().setSelectionProvider(this);
	}
	
	void init() {
		Search search= SearchManager.getDefault().getCurrentSearch();
		if (search != null) {
			setGotoMarkerAction(search.getGotoMarkerAction());
			setContextMenuTarget(search.getContextMenuContributor());
			setActionGroupFactory(null);
			setActionGroupFactory(search.getActionGroupFactory());
			setPageId(search.getPageId());
			setInput(search.getResults());			
		}
	}

	/**
	 * @see StructuredViewer#doUpdateItem(Widget, Object, boolean)
	 */
	protected void doUpdateItem(Widget item, Object element, boolean fullMap) {
		super.doUpdateItem(item, element, fullMap);
		if (((SearchResultViewEntry)element).isPotentialMatch()) {
		    TableItem ti = (TableItem) item;
		    ti.setForeground(fPotentialMatchFgColor);
		}
	}

	private void handleSelectionChanged() {
		int selectionCount= getSelectedEntriesCount();
		boolean hasSingleSelection= selectionCount == 1;
		boolean hasElements= getItemCount() > 0;
		fShowNextResultAction.setEnabled(hasSingleSelection || (hasElements && selectionCount == 0));
		fShowPreviousResultAction.setEnabled(hasSingleSelection || (hasElements && selectionCount == 0));
		fGotoMarkerActionProxy.setEnabled(hasSingleSelection);
		fRemoveSelectedMatchesAction.setEnabled(selectionCount > 0);

		if (fHandleSelectionChangedEvents) {
			fMarkerToShow= -1;
			fCurrentMatchRemoved= false;
		} else
			fHandleSelectionChangedEvents= true;

		updateStatusLine();
	}

	void updateStatusLine() {
		boolean hasSingleSelection= getSelectedEntriesCount() == 1;
		String location= ""; //$NON-NLS-1$
		if (hasSingleSelection) {
			ISearchResultViewEntry entry= (ISearchResultViewEntry)getTable().getItem(getTable().getSelectionIndex()).getData();
			IPath path= entry.getResource().getFullPath();
			if (path != null)
				location= path.makeRelative().toString();
		}
		setStatusLineMessage(location);
	}

	void enableActions() {
		/*
		 * Note: The check before each set operation reduces flickering
		 */
		boolean state= getItemCount() > 0;
		if (state != fShowNextResultAction.isEnabled())
			fShowNextResultAction.setEnabled(state);
		if (state != fShowPreviousResultAction.isEnabled())
			fShowPreviousResultAction.setEnabled(state);
		if (state != fSortDropDownAction.isEnabled())
			fSortDropDownAction.setEnabled(state);

		state= SearchManager.getDefault().getCurrentSearch() != null;
		if (state != fSearchDropDownAction.isEnabled())
			fSearchDropDownAction.setEnabled(state);
		if (state != fSearchAgainAction.isEnabled())
			fSearchAgainAction.setEnabled(state);

		state= !getSelection().isEmpty();
		if (state != fGotoMarkerActionProxy.isEnabled())
			fGotoMarkerActionProxy.setEnabled(state);
		if (state != fRemoveSelectedMatchesAction.isEnabled())
			fRemoveSelectedMatchesAction.setEnabled(state);
	}


	protected void inputChanged(Object input, Object oldInput) {
		fLastSelection= null;
		getTable().removeAll();
		super.inputChanged(input, oldInput);
		fMarkerToShow= -1;
		fCurrentMatchRemoved= false;
		updateTitle();
		enableActions();
		if (getItemCount() > 0)
			selectResult(getTable(), 0);
	}

	protected int getSelectedEntriesCount() {
		ISelection s= getSelection();
		if (s == null || s.isEmpty() || !(s instanceof IStructuredSelection))
			return 0;
		IStructuredSelection selection= (IStructuredSelection)s;
		return selection.size();
	}

	//--- Contribution management -----------------------------------------------


	protected boolean enableRemoveMatchMenuItem() {
		if (getSelectedEntriesCount() != 1)
			return false;
		Table table= getTable();
		int index= table.getSelectionIndex();
		SearchResultViewEntry entry= null;
		if (index > -1)
			entry= (SearchResultViewEntry)table.getItem(index).getData();
		return (entry != null && entry.getMatchCount() > 1);
			
	}
	
	void fillContextMenu(IMenuManager menu) {
		ISelection selection= getSelection();
		
		if (fActionGroup != null) {
			ActionContext context= new ActionContext(selection);
			context.setInput(getInput());
			fActionGroup.setContext(context);
			fActionGroup.fillContextMenu(menu);
			fActionGroup.setContext(null);
		}
		
		if (fContextMenuContributor != null)
			fContextMenuContributor.fill(menu, this);
		
		if (!selection.isEmpty()) {
			menu.appendToGroup(IContextMenuConstants.GROUP_REORGANIZE, fCopyToClipboardAction);
			menu.appendToGroup(IContextMenuConstants.GROUP_GOTO, fGotoMarkerActionProxy);
			if (enableRemoveMatchMenuItem())
				menu.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, new RemoveMatchAction(this));
			menu.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, new RemoveResultAction(this, true));

			if (isPotentialMatchSelected())
				menu.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, new RemovePotentialMatchesAction(fOuterPart.getViewSite()));
		}

		// If we have elements
		if (getItemCount() > 0)
			menu.appendToGroup(IContextMenuConstants.GROUP_REMOVE_MATCHES, new RemoveAllResultsAction());
	
		menu.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, fSearchAgainAction);
		if (getItemCount() > 0) {
			fSortDropDownAction= fSortDropDownAction.renew();
			if (fSortDropDownAction.getSorterCount() > 1)
				menu.appendToGroup(IContextMenuConstants.GROUP_VIEWER_SETUP, fSortDropDownAction);
		}
	}

	private boolean isPotentialMatchSelected() {
		ISelection selection= getSelection();
		if (selection instanceof IStructuredSelection) {
			Object entry= ((IStructuredSelection)selection).getFirstElement();
			if (entry instanceof ISearchResultViewEntry) {
				IMarker marker= ((ISearchResultViewEntry)entry).getSelectedMarker();
				return marker != null && marker.getAttribute(SearchUI.POTENTIAL_MATCH, false);
			}
		}
		return false;
	}

	IAction getGotoMarkerAction() {
		// null as return value is covered (no action will take place)
		return fGotoMarkerAction;
	}

	void setGotoMarkerAction(IAction gotoMarkerAction) {
		fGotoMarkerAction= gotoMarkerAction;
	}


	void setContextMenuTarget(IContextMenuContributor contributor) {
		fContextMenuContributor= contributor;
	}

	void setActionGroupFactory(IActionGroupFactory groupFactory) {
		IActionBars actionBars= fOuterPart.getViewSite().getActionBars();		
		if (fActionGroup != null) {
			fActionGroup.dispose();
			fActionGroup= null;
		}
			
		if (groupFactory != null) {
			fActionGroup= groupFactory.createActionGroup(fOuterPart);
			if (actionBars != null)
				fActionGroup.fillActionBars(actionBars);
		}
		if (actionBars != null)
			actionBars.updateActionBars();
	}

	void setPageId(String pageId) {
		ILabelProvider labelProvider= fOuterPart.getLabelProvider(pageId);
		if (labelProvider != null)
			internalSetLabelProvider(labelProvider);
		fSortDropDownAction.setPageId(pageId);
	}
	
	void fillToolBar(IToolBarManager tbm) {
		tbm.add(fShowNextResultAction);
		tbm.add(fShowPreviousResultAction);
//		tbm.add(fGotoMarkerAction); see bug 15275
		tbm.add(fRemoveSelectedMatchesAction);
		tbm.add(new Separator());
		tbm.add(new OpenSearchDialogAction());
		tbm.add(fSearchDropDownAction);
		
		// need to hook F5 to table
		getTable().addKeyListener(new KeyAdapter() {
			public void keyReleased(KeyEvent e) {
				if (e.keyCode == SWT.F5) {
					fSearchAgainAction.run();
					return;	// performance
				}
				if (e.character == SWT.DEL) {
					new RemoveResultAction(SearchResultViewer.this, true).run();
					return; // performance
				}
			}
		});
	}	

	int getItemCount() {
		return SearchManager.getDefault().getCurrentItemCount();
	}

	void internalSetLabelProvider(ILabelProvider provider) {
		setLabelProvider(new SearchResultLabelProvider(provider));
	}

	/**
	 * Makes the first marker of the current result entry
	 * visible in an editor. If no result
	 * is visible, this method does nothing.
	 */
	public void showResult() {
		Table table= getTable();
		if (!canDoShowResult(table))
			return;


		int index= table.getSelectionIndex();
		if (index < 0)
			return;
		SearchResultViewEntry entry= (SearchResultViewEntry)getTable().getItem(index).getData();


		fMarkerToShow= 0;
		fCurrentMatchRemoved= false;
		entry.setSelectedMarkerIndex(0);
		openCurrentSelection();
	}


	/**
	 * Makes the next result (marker) visible in an editor. If no result
	 * is visible, this method makes the first result visible.
	 */
	public void showNextResult() {
		Table table= getTable();
		if (!canDoShowResult(table))
			return;

		int index= table.getSelectionIndex();
		SearchResultViewEntry entry= null;
		if (index > -1)
			entry= (SearchResultViewEntry)table.getItem(index).getData();

		if (fCurrentMatchRemoved)
			fCurrentMatchRemoved= false;
		else
			fMarkerToShow++;
		if (entry == null || fMarkerToShow >= entry.getMatchCount()) {
			// move selection
			if (index == -1) {
				index= 0;
			} else {
				index++;
				if (index >= table.getItemCount())
					index= 0;
			}
			fMarkerToShow= 0;
			entry= (SearchResultViewEntry)getTable().getItem(index).getData();
			selectResult(table, index);
		}
		entry.setSelectedMarkerIndex(fMarkerToShow);
		openCurrentSelection();
		updateStatusLine();
	}


	/**
	 * Makes the previous result (marker) visible. If there isn't any
	 * visible result, this method makes the last result visible.
	 */
	public void showPreviousResult() {
		fCurrentMatchRemoved= false;
		Table table= getTable();
		if (!canDoShowResult(table))
			return;
				
		int index= table.getSelectionIndex();
		SearchResultViewEntry entry;


		fMarkerToShow--;
		if (fMarkerToShow >= 0)
			entry= (SearchResultViewEntry)getTable().getItem(getTable().getSelectionIndex()).getData();			
		else {
			// move selection		
			int count= table.getItemCount();
			if (index == -1) {
				index= count - 1;
			} else {
				index--;
				if (index < 0)
					index= count - 1;
			}
			entry= (SearchResultViewEntry)getTable().getItem(index).getData();
			fMarkerToShow= entry.getMatchCount() - 1;
			selectResult(table, index);
		}
		entry.setSelectedMarkerIndex(fMarkerToShow);
		openCurrentSelection();
		updateStatusLine();
	}
	
	private boolean canDoShowResult(Table table) {
		if (table == null || getItemCount() == 0)
			return false;
		return true;			
	}
		
	private void selectResult(Table table, int index) {
		fHandleSelectionChangedEvents= false;
		Object element= getElementAt(index);
		if (element != null)
			setSelection(new StructuredSelection(getElementAt(index)), true);
		else
			setSelection(StructuredSelection.EMPTY);
	}

	private void openCurrentSelection() {
		IAction action= getGotoMarkerAction();
		if (action != null)
			action.run();
	}

	/**
	 * Updates the foreground color for potential matches.
	 */
	void updatedPotentialMatchFgColor() {
		if (fPotentialMatchFgColor != null)
			fPotentialMatchFgColor.dispose();
		fPotentialMatchFgColor= null;
		if (SearchPreferencePage.arePotentialMatchesEmphasized())
			fPotentialMatchFgColor= new Color(SearchPlugin.getActiveWorkbenchShell().getDisplay(), SearchPreferencePage.getPotentialMatchForegroundColor());
		refresh();
	}

	/**
	 * Update the title
	 */
	protected void updateTitle() {
		boolean hasCurrentSearch= SearchManager.getDefault().getCurrentSearch() != null;
		String title;
		if (hasCurrentSearch) {
			String description= SearchManager.getDefault().getCurrentSearch().getFullDescription();
			title= SearchMessages.getFormattedString("SearchResultView.titleWithDescription", description); //$NON-NLS-1$
		} else
			title= SearchMessages.getString("SearchResultView.title"); //$NON-NLS-1$
		if (title == null || !title.equals(fOuterPart.getTitle()))
			fOuterPart.setTitle(title);
	}

	/**
	 * Clear the title
	 */
	protected void clearTitle() {
		String title= SearchMessages.getString("SearchResultView.title"); //$NON-NLS-1$
		if (title == null || !title.equals(fOuterPart.getTitle()))
			fOuterPart.setTitle(title);
	}

	/**
	 * Sets the message text to be displayed on the status line.
	 * The image on the status line is cleared.
	 */
	private void setStatusLineMessage(String message) {
		fOuterPart.getViewSite().getActionBars().getStatusLineManager().setMessage(message);
	}


	protected void handleDispose(DisposeEvent event) {
		fLastSelection= null;
		Menu menu= getTable().getMenu();
		if (menu != null)
			menu.dispose();
		if (fPotentialMatchFgColor != null)
			fPotentialMatchFgColor.dispose();
		if (fActionGroup != null) {
			fActionGroup.dispose();
			fActionGroup= null;
		}
		super.handleDispose(event);
	}

	//--- Change event handling -------------------------------------------------
	
	/**
	 * Handle a single add.
	 */
	protected void handleAddMatch(ISearchResultViewEntry entry) {
		insert(entry, -1);
	}

	/**
	 * Handle a single remove.
	 */
	protected void handleRemoveMatch(ISearchResultViewEntry entry) {
		Widget item= findItem(entry);
		if (entry.getMatchCount() == 0)
			remove(entry);
		else
			updateItem(item, entry);
		updateStatusLine();
	}

	/**
	 * Handle remove all.
	 */
	protected void handleRemoveAll() {
		setContextMenuTarget(null);
		setActionGroupFactory(null);
		setInput(null);
	}

	/**
	 * Handle an update of an entry.
	 */
	protected void handleUpdateMatch(ISearchResultViewEntry entry, boolean matchRemoved) {
		Widget item= findItem(entry);
		updateItem(item, entry);
		if (matchRemoved && getSelectionFromWidget().contains(entry))
			fCurrentMatchRemoved= true;
	}

	//--- Persistency -------------------------------------------------

	void restoreState(IMemento memento) {
		fSortDropDownAction.restoreState(memento);
	}
	
	void saveState(IMemento memento) {
		fSortDropDownAction.saveState(memento);
	}	

	/*
	 * @see ContentViewer#handleLabelProviderChanged(LabelProviderChangedEvent)
	 */
	protected void handleLabelProviderChanged(LabelProviderChangedEvent event) {
		Object[] changed= event.getElements();
		if (changed != null && !fResourceToItemsMapper.isEmpty()) {
			ArrayList others= new ArrayList(changed.length);
			for (int i= 0; i < changed.length; i++) {
				Object curr= changed[i];
				if (curr instanceof IResource)
					fResourceToItemsMapper.resourceChanged((IResource) curr);
				else if (curr instanceof IAdaptable) {
					IResource resource= (IResource)((IAdaptable)curr).getAdapter(IResource.class);
					if (resource != null)
						fResourceToItemsMapper.resourceChanged(resource);
				} else
					others.add(curr);
			}
			if (others.isEmpty()) {
				return;
			}
			event= new LabelProviderChangedEvent((IBaseLabelProvider) event.getSource(), others.toArray());
		}
		super.handleLabelProviderChanged(event);
	}

	/*
	 * @see StructuredViewer#mapElement(Object, Widget)
	 */
	protected void mapElement(Object element, Widget item) {
		super.mapElement(element, item);
		if (item instanceof Item) {
			fResourceToItemsMapper.addToMap(element, (Item)item);
		}
	}

	/*
	 * @see StructuredViewer#unmapElement(Object, Widget)
	 */
	protected void unmapElement(Object element, Widget item) {
		if (item instanceof Item) {
			fResourceToItemsMapper.removeFromMap(element, (Item)item);
		}		
		super.unmapElement(element, item);
	}

	/*
	 * @see StructuredViewer#unmapAllElements()
	 */
	protected void unmapAllElements() {
		fResourceToItemsMapper.clearMap();
		super.unmapAllElements();
	}
}
