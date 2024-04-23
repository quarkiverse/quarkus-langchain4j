package io.quarkiverse.langchain4j.sample.chatbot;

import java.util.Collection;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.spi.BootstrapContext;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.integrator.spi.Integrator;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.service.spi.SessionFactoryServiceRegistry;

public class MovieTableIntegrator implements Integrator {

    static volatile String schemaStr = "";

    @Override
    public void integrate(
            Metadata metadata,
            BootstrapContext bootstrapContext,
            SessionFactoryImplementor sessionFactory) {
        PersistentClass moviePC = null;
        for (PersistentClass entityBinding : metadata.getEntityBindings()) {
            if (Movie.class.getName().equals(entityBinding.getClassName())) {
                moviePC = entityBinding;
                break;
            }
        }
        if (moviePC == null) {
            throw new IllegalStateException("Unable to determine metadata of Movie");
        }

        Table table = moviePC.getTable();
        Collection<Column> columns = table.getColumns();
        StringBuilder result = new StringBuilder();
        boolean first = true;
        for (Column column : columns) {
            if (first) {
                first = false;
            } else {
                result.append("\n - ");
            }
            StringBuilder sb = new StringBuilder(column.getName()).append(" (").append(column.getSqlType(metadata))
                    .append(")");
            if (column.getComment() != null) {
                sb.append(" - ").append(column.getComment());
            }
            result.append(sb);
        }
        schemaStr = result.toString();
    }

    @Override
    public void disintegrate(SessionFactoryImplementor sessionFactory,
            SessionFactoryServiceRegistry serviceRegistry) {

    }
}
