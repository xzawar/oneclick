insert into categories (slug,name_en,name_ur,pin_color,sort_order) values
 ('plumber','Plumber','پلمبر','#1565C0',1),('electrician','Electrician','الیکٹریشن','#F9A825',2),
 ('welder','Welder','ویلڈر','#BF360C',3),('mechanic','Mechanic','مکینک','#37474F',4),
 ('carpenter','Carpenter','کارپینٹر','#4E342E',5),('painter','Painter','پینٹر','#6A1B9A',6);

insert into users (id,name,phone,user_role) values
 ('00000000-0000-0000-0000-000000000001','Hamza Ali','+923001110001','customer'),
 ('00000000-0000-0000-0000-000000000002','Ayesha Malik','+923001110002','customer'),
 ('00000000-0000-0000-0000-000000000011','Shahid Rana','+923001110011','both'),
 ('00000000-0000-0000-0000-000000000012','Ahmad Khan','+923001110012','provider'),
 ('00000000-0000-0000-0000-000000000013','Sara Iqbal','+923001110013','provider'),
 ('00000000-0000-0000-0000-000000000014','Bilal Hussain','+923001110014','provider'),
 ('00000000-0000-0000-0000-000000000015','Usman Tariq','+923001110015','provider'),
 ('00000000-0000-0000-0000-000000000016','Kashif Mehmood','+923001110016','provider'),
 ('00000000-0000-0000-0000-000000000017','Imran Butt','+923001110017','provider'),
 ('00000000-0000-0000-0000-000000000018','Faisal Javed','+923001110018','provider');

insert into provider_profiles (user_id,categories,bio,is_online,tier,rating_avg,total_jobs,lat,lng,service_radius_km,is_verified,verified_at) values
 ('00000000-0000-0000-0000-000000000011','{electrician}','Wiring, UPS & fan repair',false,'silver',4.5,52,31.5190,74.3570,6,true,now()),
 ('00000000-0000-0000-0000-000000000012','{plumber}','Leaks, geysers, bathroom fitting',true,'silver',4.8,127,31.5235,74.3550,6,true,now()),
 ('00000000-0000-0000-0000-000000000013','{electrician}','Solar & DB board specialist',true,'gold',4.9,214,31.5170,74.3620,8,true,now()),
 ('00000000-0000-0000-0000-000000000014','{welder}','Gates, grills, on-site welding',true,'silver',4.3,64,31.5260,74.3640,6,true,now()),
 ('00000000-0000-0000-0000-000000000015','{mechanic}','Car & bike roadside repair',true,'bronze',4.6,38,31.5150,74.3500,10,true,now()),
 ('00000000-0000-0000-0000-000000000016','{carpenter}','Doors, cabinets, furniture',true,'bronze',4.1,22,31.5290,74.3520,5,true,now()),
 ('00000000-0000-0000-0000-000000000017','{painter}','Interior & exterior paint',true,'silver',4.7,95,31.5120,74.3660,6,true,now()),
 ('00000000-0000-0000-0000-000000000018','{plumber}','DHA based plumber',true,'silver',4.4,80,31.4800,74.4000,3,true,now());

insert into wallets (user_id,balance,total_earned) values
 ('00000000-0000-0000-0000-000000000001',5000,0),('00000000-0000-0000-0000-000000000002',3000,0),
 ('00000000-0000-0000-0000-000000000011',450,8400),('00000000-0000-0000-0000-000000000012',1200,21000),
 ('00000000-0000-0000-0000-000000000013',1500,36000),('00000000-0000-0000-0000-000000000014',800,9500),
 ('00000000-0000-0000-0000-000000000015',600,5200),('00000000-0000-0000-0000-000000000016',300,2400),
 ('00000000-0000-0000-0000-000000000017',900,12000),('00000000-0000-0000-0000-000000000018',700,10000);

insert into transactions (to_user,amount,type,external_ref,created_at) values
 ('00000000-0000-0000-0000-000000000001',5000,'wallet_topup','DEMO-TID-0001',now()-interval '3 days'),
 ('00000000-0000-0000-0000-000000000002',3000,'wallet_topup','DEMO-TID-0002',now()-interval '2 days');
