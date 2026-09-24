package de.frank.invoice.worker.ui.vaadin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.sidenav.SideNavItem;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class MainLayoutTest {
    @Test
    void navigationContainsNativeCockpitRoute() {
        final MainLayout layout = new MainLayout();

        assertThat(descendants(layout).filter(SideNavItem.class::isInstance)
                .map(SideNavItem.class::cast)
                .anyMatch(item -> "Cockpit".equals(item.getLabel())
                        && "cockpit".equals(item.getPath()))).isTrue();
    }

    private static Stream<Component> descendants(final Component component) {
        return Stream.concat(Stream.of(component),
                component.getChildren().flatMap(MainLayoutTest::descendants));
    }
}
