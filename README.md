**BarBot application**

_TL; DR and resources links_

telegram chat bot capable of suggesting most suitable bar for this evening’s "getting wasted" party.

decision is provided in a form of a sorted list, making sure there are vacant places, preferred selection of drinks and food available too.

optionally bot should be able to book a table.

accessible via:

`t.me/bar_crawler_bot`

demo of usage:

`vimeo.com/251073944`

current state of project:

`https://trello.com/b/nyxHUfoO/bot-pub-crawler`

_available features:_

- at its core system is not something gradually destroying one's liver, rather a sort of a decision making engine;
- it is assumed that bars eligible for selection are able to provide some public web page with the necessary information 
  in form of JSON (like working hours, vacant places, booking capabilities), i.e. follow some defined protocol;
  there is mock web service for dev purposes provided simulating a couple of bars; 
- another mandatory step for a bar to be considered as possible ‘target’ - registration via public API provided by the bot;
- bot is capable of maintaining different degree of questionnaires based on user's request:
  from very basic (for fast selection, e.g. "beer, now, fast, 6 people") to a very detailed (if the number of factors considered by the user is big);
  as of now, these are kept as DB entries and there is no way to expand those via config, but support of such configuration is on the roadmap;
- bot supports multiple users;
- actual decision is made based on several factors, each having configurable weights:
1) availability of locations for requested time;
2) availability of required number of places;
3) preferred drink types and cuisine;
4) distance to a bar (as of now dummy data is used, not really functional yet).

_features to be implemented:_

- simulate something similar to personal assistance, for this purpose bot should provide special questionnaire allowing to fill in user data;
- bot should provide possibility to store user's visits and marks into database and a way to search through this history;
- booking a table (not clear how to approach at this point);
- provide a way to expand questionnaire list via config in a form of keywords list; 
- additional decision factors:
1) geographical location with the help of Google API;
2) using marks from Trip Advisor API;
3) history of user's visits and marks;
4) user profile from the database;
5) configurable groups of users
   (also, possibly, a way to simplify the questionnaire given that each user in a group has preferences from point 5);
6) provide a way to "teach" the bot with direct input of data;
7) advanced extension module which allows using arbitrary factors
   e.g. recognizing beer type by picture and finding nearest bar serving this type).


***Production scenario description***

in production BarBot operates as a single container which hosts database and public accessible registration API.
using this API bars interested to become targets should register themselves.
registration requires address of public available web page which hosts bar parameters in form of JSON.

samples of such JSON are provided in
  
`docker-compose/bars.json`

after start BarBot will periodically query that page to keep bar state up-to-date during decision making.
depending on replies from bar web servers BarBot may temporary exclude bars from potential targets list.
also it is possible to exclude bar for longer term - until status update will be done from bar side.
to track it's state from BarBot perspective each bar should periodically use special "status" endpoint.

production-ready image resides on

`dragsasgard/postgres_96_with_barbot`


***Local development setup instructions***

in order to operate properly BarBot needs sources of bar data.
local development env uses fake bars and those are provided via additional container.

steps to setup dev env:
- start docker compose:

  `docker-compos -f docker-compose/docker-compose-develop.yaml up`
  
  this includes database and fake bars server running on "IP:8080"; on *nix it will be "localhost" and on Win - dedicated IP; 
  fake server is just a JSON serving entity which has 5 different resources to simulate real-life different IP addresses;
- start BarBot;
  this step will init database with some default entries (like 2 basic types of questionnaire);
- register fake bars via BarBot register API;
  fake bars are not inserted by BarBot application during startup intentionally as this is external source simulation.
  
bars can be created either using INSERT statements part from

`docker-compose/bar-table-inserts-develop.sql`

or using samples of Postman exports provided in

`docker-compose/dataroot-barbot-public.postman_collection.json`


***Production simulation instructions***

for production simulation there is need to execute

`docker-compos -f docker-compose/docker-compose-production-simultion.yaml up`
 
which will do the job.
again fake bars are provided via additional container, but there is no need to register anything - it works out-of-the-box.