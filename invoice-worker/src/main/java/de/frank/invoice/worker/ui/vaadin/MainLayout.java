package de.frank.invoice.worker.ui.vaadin;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import de.frank.invoice.worker.ui.vaadin.views.export.InvoiceExportView;
import de.frank.invoice.worker.ui.vaadin.views.manualreview.ManualReviewView;

/**
 * Main navigation layout for browser-based invoice tools.
 */
public class MainLayout extends AppLayout {

    /**
     * Creates the application title and export navigation.
     */
    public MainLayout() {
        final H1 title = new H1("Invoice System");
        title.getStyle().set("font-size", "var(--lumo-font-size-l)").set("margin", "0");
        addToNavbar(title);

        final SideNav navigation = new SideNav();
        navigation.addItem(new SideNavItem("Rechnungen exportieren", InvoiceExportView.class));
        navigation.addItem(new SideNavItem("Manual Review", ManualReviewView.class));
        addToDrawer(navigation);
    }
}
