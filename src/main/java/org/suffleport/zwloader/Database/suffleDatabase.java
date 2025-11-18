package org.suffleport.zwloader.Database;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;


/**
 *  Component создаёт bean со scope singleton — один объект на ApplicationContext (SINGLTON) используем чтобы подключитьяс к бд
 */
@Component
public class CreateDatabase {
    private DataSource dataSource;

    // по документации spring сам подставит сюда DataSource из настроек spring.datasource
    public CreateDatabase(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * Метод @PostConstruct вызывается автоматически после создания бина.
     * Здесь мы инициализируем схему БД.
     */
    @PostConstruct
    public void init() {
        System.out.println("Колдуем над бд");

        try ( Connection connect = dataSource.getConnection();
              Statement st = connect.createStatement()) {


            createTypes(st);
            createPositionsTable(st);
            createPersonnelTable(st);
            createCardsTable(st);
            createFacesTable(st);
            createDevicesTable(st);
            createCamerasTable(st);
            createGuestsTables(st);
            createEventsTable(st);
            createRolesAndUsersTables(st);
            createSafetyIncidentsTable(st);


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


    //  Таблица positions (должности)


    private void createPositionsTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS positions (
                position_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
                position_name TEXT NOT NULL,
                access_level  INTEGER,
                created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_positions_name_lower
                ON positions (lower(position_name))
        """);
    }


    //  Таблица personnel (персонал)


    private void createPersonnelTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS personnel (
                person_id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_personnel_name_lower
                ON personnel (lower(last_name), lower(first_name))
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_personnel_full_name_lower
                ON personnel (lower(full_name))
        """);
    }


    //  Таблица cards (карты/пропуска)


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


    //  Таблица faces (лица / CompreFace subjects)


    private void createFacesTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS faces (
                face_id BIGSERIAL PRIMARY KEY,
                face_name TEXT NOT NULL,
                person_id UUID NOT NULL REFERENCES personnel(person_id) ON DELETE CASCADE,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
                UNIQUE (face_name)
            )
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_faces_person_id
                ON faces(person_id)
        """);
    }


    //  Таблица devices (устройства)


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


    //  Таблица cameras (RTSP-потоки)


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


    //  Таблицы guests и guest_visits (гости)


    private void createGuestsTables(Statement st) throws SQLException {
        // Карточка гостя (кто он вообще)
        st.execute("""
            CREATE TABLE IF NOT EXISTS guests (
                guest_id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
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
        """);

        // Конкретные визиты гостей
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


    //  Таблица events (журнал проходов)


    private void createEventsTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS events (
                event_id     BIGSERIAL PRIMARY KEY,
                uid          TEXT REFERENCES cards(uid),
                person_id    UUID REFERENCES personnel(person_id),
                face_name    TEXT REFERENCES faces(face_name),
                device_id    TEXT REFERENCES devices(device_id),
                direction    direction_t NOT NULL,
                source       source_t    NOT NULL,
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


    //  Роли и пользователи веб-морды


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


    //  Таблица нарушений техники безопасности

    private void createSafetyIncidentsTable(Statement st) throws SQLException {
        st.execute("""
            CREATE TABLE IF NOT EXISTS safety_incidents (
                incident_id     BIGSERIAL PRIMARY KEY,
                person_id       UUID REFERENCES personnel(person_id),
                guest_id        UUID REFERENCES guests(guest_id),
                device_id       TEXT REFERENCES devices(device_id),
                event_time      TIMESTAMPTZ NOT NULL DEFAULT now(),
                type            TEXT NOT NULL,
                severity        TEXT,
                description     TEXT,
                image_url       TEXT,
                status          TEXT NOT NULL DEFAULT 'NEW',
                handled_by      UUID REFERENCES personnel(person_id),
                handled_at      TIMESTAMPTZ,
                created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
            )
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_safety_incidents_time
                ON safety_incidents(event_time)
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_safety_incidents_person
                ON safety_incidents(person_id)
        """);

        st.execute("""
            CREATE INDEX IF NOT EXISTS idx_safety_incidents_type
                ON safety_incidents(type)
        """);
    }
}