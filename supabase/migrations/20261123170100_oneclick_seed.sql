-- Demo people around Gulberg III, Lahore. Hamza (customer) and Ahmad (provider) are real sign-in accounts;
-- the rest are simulated (is_bot) and act on their own inside the api function.
insert into public.users (id, name, phone, user_role, is_bot) values
 ('00000000-0000-0000-0000-000000000001','Hamza Ali','+923001110001','customer',false),
 ('00000000-0000-0000-0000-000000000012','Ahmad Khan','+923001110012','provider',false),
 ('00000000-0000-0000-0000-000000000020','Sara Iqbal','+923001110020','provider',true),
 ('00000000-0000-0000-0000-000000000021','Usman Tariq','+923001110021','provider',true),
 ('00000000-0000-0000-0000-000000000022','Kashif Mehmood','+923001110022','provider',true),
 ('00000000-0000-0000-0000-000000000023','Imran Butt','+923001110023','provider',true),
 ('00000000-0000-0000-0000-000000000024','Bilal Hussain','+923001110024','provider',true),
 ('00000000-0000-0000-0000-000000000025','Faisal Javed','+923001110025','provider',true),
 ('00000000-0000-0000-0000-000000000090','Zainab Akhtar','+923001110090','customer',true);

insert into public.provider_profiles (user_id, categories, is_online, tier, rating_avg, rating_count, total_jobs, lat, lng, is_verified, verified_at) values
 ('00000000-0000-0000-0000-000000000012','{plumber,electrician}',false,'silver',4.8,127,127,31.5236,74.3562,true,now()),
 ('00000000-0000-0000-0000-000000000020','{electrician}',true,'gold',4.9,57,57,31.5178,74.3618,true,now()),
 ('00000000-0000-0000-0000-000000000021','{mechanic}',true,'silver',4.6,74,74,31.5161,74.3531,true,now()),
 ('00000000-0000-0000-0000-000000000022','{carpenter}',true,'silver',4.5,91,91,31.5279,74.3566,true,now()),
 ('00000000-0000-0000-0000-000000000023','{painter}',true,'gold',4.7,108,108,31.5142,74.3640,true,now()),
 ('00000000-0000-0000-0000-000000000024','{welder}',true,'silver',4.4,40,40,31.5257,74.3628,true,now()),
 ('00000000-0000-0000-0000-000000000025','{plumber}',true,'silver',4.6,83,83,31.5188,74.3549,true,now());

insert into public.wallets (user_id, balance, total_earned) values
 ('00000000-0000-0000-0000-000000000001',5000,0), ('00000000-0000-0000-0000-000000000012',1200,21000),
 ('00000000-0000-0000-0000-000000000020',900,15000), ('00000000-0000-0000-0000-000000000021',900,15000),
 ('00000000-0000-0000-0000-000000000022',900,15000), ('00000000-0000-0000-0000-000000000023',900,15000),
 ('00000000-0000-0000-0000-000000000024',900,15000), ('00000000-0000-0000-0000-000000000025',900,15000),
 ('00000000-0000-0000-0000-000000000090',20000,0);

insert into public.transactions (to_user, amount, type, status, external_ref, created_at) values
 ('00000000-0000-0000-0000-000000000001',5000,'wallet_topup','approved','JC-58210443', now() - interval '3 days'),
 ('00000000-0000-0000-0000-000000000012',1200,'wallet_topup','approved','EP-77120931', now() - interval '2 days');
