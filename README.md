**BarBot application features**

_TL; DR_

Telegram chat bot capable of suggesting most suitable bar for this evening’s "getting wasted".
Decision is provided in a form of a sorted list, making sure there are vacant places, preferred selection of drinks/food available too.
Optionally bot should be able to book a table.

_Available features:_

- at its core system is not something gradually destroying one's liver, rather a sort of a decision making engine;
- it is assumed that bars eligible for selection are able to provide some public web page with the necessary information in form of JSON (like working hours, vacant places, booking capabilities), i.e. follow some defined protocol; there is mock web service for dev purposes provided simulating a couple of bars;
- another mandatory step for a bar to be listed as possible ‘target’ - registration via public API provided by the bot;
- bot is capable of maintaining different degree of questionnaires based on user's request: from very basic (for fast selection, e.g. "beer, now, fast, 6 people") to a very detailed (if the number of factors considered by the user is big). as of now, these are kept as DB entries and there is no way to expand those via config, but this is planned;
- bot supports multiple users;
- actual decision is made based on several factors, each having configurable weights:
1) availability of locations for requested time;
2) preferred drink types and cuisine;
3) distance to a bar (dummy data, not really functional yet).

_Features to be implemented:_

- simulate something similar to personal assistance, bot should maintain database to store user's visits and marks;
- bot should provide a way to search through the history of visits;
- booking a table (not clear how to approach this at this point);
- provide a way to expand questionnaire list via config in a form of configurable keywords list; 
- add additional decision factors:
1) geographical location with the help of Google API;
2) using marks from Trip Advisor API;
3) history of user's visits and marks;
4) user profile from the database;
5) configurable groups of users (also, possibly, a way to simplify the questionnaire);
6) provide a way to "teach" the bot with direct input of data;
7) advanced extension module which allows to put arbitrary factors
   e.g. recognizing beer type by picture and finding nearest bar serving this type).

**Local execution instructions**

In order to operate properly BarBot needs sources of data. Local development env uses fake bars and those are provided via containers.
Steps to setup dev env:
- start docker composition; this includes database and fake bars server running on some_IP:8080; this fake server is just a JSON serving entity which has 3 different resources to simulate real-life different IP addresses;
- start BarBot;
- register fake bars via BarBot register API.

unfortunately, given the way docker is implemented on Windows platform there seems to be no way to provide registration data as plain SQL inserts because IP addresses are different on each deployment.

samples to register are in Postman export provided in project and named

`dataroot-barbot-public.postman_collection.json`

IP address in attribute "locationUrl" should be changed to point to the one on which fake bars server is running.
