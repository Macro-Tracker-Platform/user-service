insert into users (id, email, password, email_confirmed) values (1, 'test1@example.com', '$2a$10$RRsmVlrXUGr0j5JtwQuEcOHcwIbSi.Qz60jMimNl5ML9fTcwc.TSu', true)
insert into users (id, email, password, email_confirmed) values (2, 'test2@example.com', '$2a$10$RRsmVlrXUGr0j5JtwQuEcOHcwIbSi.Qz60jMimNl5ML9fTcwc.TSu', true)
insert into users (id, email, password, email_confirmed) values (3, 'test3@example.com', '$2a$10$RRsmVlrXUGr0j5JtwQuEcOHcwIbSi.Qz60jMimNl5ML9fTcwc.TSu', true)

insert into user_profiles (user_id, activity_level, age, calories, carbohydrates, fat, gender, goal, height, protein, weight) values (1, 'MODERATELY_ACTIVE', 50, 3000, 300, 80, 'MALE', 'MAINTAIN', 180, 130, 80)
insert into user_profiles (user_id, activity_level, age, calories, carbohydrates, fat, gender, goal, height, protein, weight) values (2, 'LIGHTLY_ACTIVE', 20, 3500, 320, 90, 'MALE', 'LOSE', 190, 140, 90)
insert into user_profiles (user_id, activity_level, age, calories, carbohydrates, fat, gender, goal, height, protein, weight) values (3, 'VERY_ACTIVE', 30, 2500, 140, 40, 'MALE', 'GAIN', 170, 110, 70)