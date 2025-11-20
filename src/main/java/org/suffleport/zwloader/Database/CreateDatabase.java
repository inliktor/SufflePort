package org.suffleport.zwloader.Database;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import org.springframework.beans.factory.annotation.Value;


/**
 *  Component создаёт bean со scope singleton — один объект на ApplicationContext (SINGLTON) используем чтобы подключитьяс к бд
 */
@Component
@ConditionalOnProperty(prefix = "app.db.init", name = "enabled", havingValue = "true", matchIfMissing = false)
public class CreateDatabase {
    private final DataSource dataSource;

    @Value("${app.db.init.enabled:false}")
    private boolean initEnabled;

    // создавать ли enum-типы (требует CREATE TYPE)
    @Value("${app.db.init.createTypes:false}")
    private boolean createTypesEnabled;

    // использовать ли DEFAULT gen_random_uuid() (требует расширение pgcrypto)
    @Value("${app.db.init.useUuidDefault:false}")
    private boolean useUuidDefault;

    public CreateDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() {
        if (!initEnabled) return;
        System.out.println("Колдуем над бд");

        try ( Connection connect = dataSource.getConnection();
              Statement st = connect.createStatement()) {

            if (createTypesEnabled) {
                createTypes(st);
            }
            createPositionsTable(st);
            createPersonnelTable(st);
            createCardsTable(st);
            createDevicesTable(st);
            createCamerasTable(st);
            createGuestsTables(st);
            createEventsTable(st);
            createRolesAndUsersTables(st);
//            createSafetyIncidentsTable(st);

            System.out.println("Инициализация бд прошла успешно!");
        } catch (Exception e) {
            System.out.println("Ошибка");
            e.printStackTrace();
        }
    }

    private void createTypes(Statement st) throws SQLException {
        st.execute("""
            DO $$
            BEGIN 
                IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'direction_t') THEN
                    CREATE TYPE direction_t AS ENUM ('IN','OUT');
                END IF;
            END$$;
        """);
        st.execute("""
            DO $$
            BEGIN
                IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'source_t') THEN
                    CREATE TYPE source_t AS ENUM ('nfc', 'face');
                END IF;
            END$$;
        """);
    }

    private String uuidDefaultExpr() {
        return useUuidDefault ? " DEFAULT gen_random_uuid()" : "";
    }

    private void createPositionsTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS positions (
                position_id UUID PRIMARY KEY%s,
                position_name TEXT NOT NULL,
                access_level  INTEGER,
                created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """.formatted(uuidDefaultExpr()));

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_positions_name_lower
                ON positions (lower(position_name))
        """);
    }

    private void createPersonnelTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS personnel (
                person_id          UUID PRIMARY KEY%s,
                last_name          TEXT,
                first_name         TEXT,
                middle_name        TEXT,
                full_name          TEXT,
                date_of_birth      DATE,
                position_id        UUID REFERENCES positions(position_id),
                phone              TEXT,
                compreface_subject TEXT,
                created_at         TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """.formatted(uuidDefaultExpr()));

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_personnel_name_lower
                ON personnel (lower(last_name), lower(first_name))
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_personnel_full_name_lower
                ON personnel (lower(full_name))
        """);
    }

    private void createCardsTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS cards (
                uid TEXT PRIMARY KEY,
                person_id UUID NOT NULL REFERENCES personnel(person_id) ON DELETE CASCADE,
                is_active BOOLEAN NOT NULL DEFAULT TRUE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_cards_person_id
                ON cards(person_id)
        """);
    }

    private void createDevicesTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS devices(
                device_id TEXT PRIMARY KEY,
                kind TEXT,
                location TEXT,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);
    }

    private void createCamerasTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS cameras (
                camera_id   TEXT PRIMARY KEY,
                name        TEXT NOT NULL,
                rtsp_url    TEXT NOT NULL,
                location    TEXT,
                device_id   TEXT REFERENCES devices(device_id),
                created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);
    }

    private void createGuestsTables(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS guests (
                guest_id      UUID PRIMARY KEY%s,
                last_name     TEXT,
                first_name    TEXT,
                middle_name   TEXT,
                full_name     TEXT,
                phone         TEXT,
                company       TEXT,
                document      TEXT,
                notes         TEXT,
                created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """.formatted(uuidDefaultExpr()));

        st.execute("""
            CREATE TABLE IF NOT EXISTS guest_visits (
                visit_id       BIGSERIAL PRIMARY KEY,
                guest_id       UUID NOT NULL REFERENCES guests(guest_id) ON DELETE CASCADE,
                host_person_id UUID NOT NULL REFERENCES personnel(person_id),
                planned_from   TIMESTAMPTZ,
                planned_to     TIMESTAMPTZ,
                reason         TEXT,
                status         TEXT,
                created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);
    }

    private void createEventsTable(Statement st) throws SQLException {
        st.execute("""
        CREATE TABLE IF NOT EXISTS events (
            event_id     BIGSERIAL PRIMARY KEY,
            uid          TEXT REFERENCES cards(uid),
            person_id    UUID REFERENCES personnel(person_id),
            face_name    TEXT,
            device_id    TEXT REFERENCES devices(device_id),
            direction    TEXT NOT NULL,
            source       TEXT NOT NULL,
            meta         JSONB NOT NULL DEFAULT '{}'::jsonb,
            created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
            CHECK (uid IS NOT NULL OR face_name IS NOT NULL OR person_id IS NOT NULL)
        )
    """);

        st.execute("""
        CREATE INDEX IF NOT EXISTS idx_events_created_at ON events(created_at)
    """);
        st.execute("""
        CREATE INDEX IF NOT EXISTS idx_events_person_id  ON events(person_id)
    """);
        st.execute("""
        CREATE INDEX IF NOT EXISTS idx_events_uid        ON events(uid)
    """);
        st.execute("""
        CREATE INDEX IF NOT EXISTS idx_events_device_id  ON events(device_id)
    """);
    }

    private void createRolesAndUsersTables(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS roles (
                role_id   SERIAL PRIMARY KEY,
                name      TEXT UNIQUE NOT NULL
            )
        """);

        st.execute("""
            CREATE TABLE IF NOT EXISTS shuffleport_users (
                user_id    SERIAL PRIMARY KEY,
                email      TEXT UNIQUE NOT NULL,
                password   TEXT NOT NULL,
                role_id    INTEGER NOT NULL REFERENCES roles(role_id),
                person_id  UUID REFERENCES personnel(person_id),
                created_at TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);
    }
}

