-- Removes the demo family so tools/demo-seed/seed-demo-family.mjs can be re-run.
--
-- LOCAL DEVELOPMENT ONLY. It deletes by the reserved demo e-mail suffix
-- (`.demo@example.com`) and the households those accounts own, so it cannot touch
-- a real account created through the app.
--
--   docker exec -i ffi-postgres psql -U familyfood -d familyfood \
--     < tools/demo-seed/reset-demo-family.sql
--
-- audit_event rows are deliberately NOT deleted: the table is append-only by design
-- (V2 installs triggers that refuse UPDATE and DELETE), and an audit trail that can be
-- erased by a seeding script would defeat its purpose. Leftover demo audit rows are
-- harmless — they reference member ids that no longer exist.

BEGIN;

CREATE TEMP TABLE demo_accounts AS
SELECT id FROM user_account WHERE email LIKE '%.demo@example.com';

CREATE TEMP TABLE demo_households AS
SELECT DISTINCT h.id
FROM household h
WHERE h.created_by_user_id IN (SELECT id FROM demo_accounts);

CREATE TEMP TABLE demo_members AS
SELECT id FROM household_member WHERE household_id IN (SELECT id FROM demo_households);

DELETE FROM habit_completion WHERE habit_id IN (
    SELECT id FROM habit WHERE household_member_id IN (SELECT id FROM demo_members));
DELETE FROM habit             WHERE household_member_id IN (SELECT id FROM demo_members);
DELETE FROM wellness_goal     WHERE household_member_id IN (SELECT id FROM demo_members);

DELETE FROM meal_portion WHERE meal_id IN (SELECT id FROM meal WHERE household_id IN (SELECT id FROM demo_households));
DELETE FROM meal_item    WHERE meal_id IN (SELECT id FROM meal WHERE household_id IN (SELECT id FROM demo_households));
DELETE FROM meal         WHERE household_id IN (SELECT id FROM demo_households);

DELETE FROM health_observation   WHERE household_member_id IN (SELECT id FROM demo_members);
DELETE FROM health_access_grant  WHERE subject_member_id  IN (SELECT id FROM demo_members)
                                    OR audience_member_id IN (SELECT id FROM demo_members);
DELETE FROM consent_record       WHERE subject_member_id  IN (SELECT id FROM demo_members);
DELETE FROM guardian_link        WHERE child_member_id    IN (SELECT id FROM demo_members)
                                    OR guardian_member_id IN (SELECT id FROM demo_members);

DELETE FROM profile_goal      WHERE profile_id IN (SELECT id FROM person_profile WHERE household_member_id IN (SELECT id FROM demo_members));
DELETE FROM profile_allergen  WHERE profile_id IN (SELECT id FROM person_profile WHERE household_member_id IN (SELECT id FROM demo_members));
DELETE FROM profile_condition WHERE profile_id IN (SELECT id FROM person_profile WHERE household_member_id IN (SELECT id FROM demo_members));
DELETE FROM person_profile    WHERE household_member_id IN (SELECT id FROM demo_members);

DELETE FROM household_invitation WHERE household_id IN (SELECT id FROM demo_households);
DELETE FROM household_member     WHERE household_id IN (SELECT id FROM demo_households);
DELETE FROM household            WHERE id IN (SELECT id FROM demo_households);

DELETE FROM refresh_token   WHERE user_account_id IN (SELECT id FROM demo_accounts);
DELETE FROM analytics_event WHERE user_account_id IN (SELECT id FROM demo_accounts);
DELETE FROM user_account    WHERE id IN (SELECT id FROM demo_accounts);

-- login_attempt is deliberately left alone: it stores only email_hash / ip_hash (D-018),
-- so demo rows cannot be matched by plaintext address — and hashed rate-limit telemetry
-- is not personal data worth chasing.

COMMIT;
