package com.nocturne.earthweather;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Curated global capital and metropolitan catalogue. The points are embedded so the globe is
 * useful offline; weather is fetched only after a point is chosen.
 */
public final class CityCatalog {
    private CityCatalog() { }

    public static final List<City> ALL = build();

    private static List<City> build() {
        List<City> cities = new ArrayList<>();
        for (String raw : DATA.trim().split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\\|", -1);
            if (fields.length != 5) continue;
            try {
                cities.add(new City(
                        fields[0], fields[1], Double.parseDouble(fields[2]),
                        Double.parseDouble(fields[3]), fields[4]));
            } catch (NumberFormatException ignored) {
                // A malformed optional entry must never keep the globe from starting.
            }
        }
        return Collections.unmodifiableList(cities);
    }

    public static List<City> search(String query, int limit) {
        String q = fold(query == null ? "" : query.trim());
        List<City> results = new ArrayList<>();
        for (City city : ALL) {
            String name = fold(city.name);
            String country = fold(city.country);
            if (q.isEmpty() || name.contains(q) || country.contains(q)) {
                results.add(city);
            }
        }
        results.sort(new Comparator<City>() {
            @Override public int compare(City left, City right) {
                boolean leftStarts = fold(left.name).startsWith(q);
                boolean rightStarts = fold(right.name).startsWith(q);
                if (leftStarts != rightStarts) return leftStarts ? -1 : 1;
                return left.displayName().compareToIgnoreCase(right.displayName());
            }
        });
        if (results.size() > limit) return new ArrayList<>(results.subList(0, limit));
        return results;
    }

    private static String fold(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    // Name | country or territory | latitude | longitude | IANA time-zone.
    // Capitals make every represented country discoverable; additional hubs keep dot density
    // meaningful at city scale without creating a heavy online geocoder dependency.
    private static final String DATA = """
            # North America, Central America, and the Caribbean
            Anchorage|United States|61.2181|-149.9003|America/Anchorage
            Seattle|United States|47.6062|-122.3321|America/Los_Angeles
            Vancouver|Canada|49.2827|-123.1207|America/Vancouver
            Portland|United States|45.5152|-122.6784|America/Los_Angeles
            San Francisco|United States|37.7749|-122.4194|America/Los_Angeles
            Los Angeles|United States|34.0522|-118.2437|America/Los_Angeles
            San Diego|United States|32.7157|-117.1611|America/Los_Angeles
            Las Vegas|United States|36.1699|-115.1398|America/Los_Angeles
            Phoenix|United States|33.4484|-112.0740|America/Phoenix
            Denver|United States|39.7392|-104.9903|America/Denver
            Salt Lake City|United States|40.7608|-111.8910|America/Denver
            Dallas|United States|32.7767|-96.7970|America/Chicago
            Houston|United States|29.7604|-95.3698|America/Chicago
            Minneapolis|United States|44.9778|-93.2650|America/Chicago
            Chicago|United States|41.8781|-87.6298|America/Chicago
            New Orleans|United States|29.9511|-90.0715|America/Chicago
            Atlanta|United States|33.7490|-84.3880|America/New_York
            Miami|United States|25.7617|-80.1918|America/New_York
            Washington, D.C.|United States|38.9072|-77.0369|America/New_York
            Philadelphia|United States|39.9526|-75.1652|America/New_York
            New York|United States|40.7128|-74.0060|America/New_York
            Boston|United States|42.3601|-71.0589|America/New_York
            Honolulu|United States|21.3069|-157.8583|Pacific/Honolulu
            Ottawa|Canada|45.4215|-75.6972|America/Toronto
            Toronto|Canada|43.6532|-79.3832|America/Toronto
            Montreal|Canada|45.5017|-73.5673|America/Toronto
            Calgary|Canada|51.0447|-114.0719|America/Edmonton
            Edmonton|Canada|53.5461|-113.4938|America/Edmonton
            Winnipeg|Canada|49.8951|-97.1384|America/Winnipeg
            Mexico City|Mexico|19.4326|-99.1332|America/Mexico_City
            Guadalajara|Mexico|20.6597|-103.3496|America/Mexico_City
            Monterrey|Mexico|25.6866|-100.3161|America/Monterrey
            Guatemala City|Guatemala|14.6349|-90.5069|America/Guatemala
            Belmopan|Belize|17.2510|-88.7590|America/Belize
            San Salvador|El Salvador|13.6929|-89.2182|America/El_Salvador
            Tegucigalpa|Honduras|14.0723|-87.1921|America/Tegucigalpa
            Managua|Nicaragua|12.1149|-86.2362|America/Managua
            San José|Costa Rica|9.9281|-84.0907|America/Costa_Rica
            Panama City|Panama|8.9824|-79.5199|America/Panama
            Havana|Cuba|23.1136|-82.3666|America/Havana
            Nassau|Bahamas|25.0443|-77.3504|America/Nassau
            Kingston|Jamaica|17.9712|-76.7936|America/Jamaica
            Port-au-Prince|Haiti|18.5944|-72.3074|America/Port-au-Prince
            Santo Domingo|Dominican Republic|18.4861|-69.9312|America/Santo_Domingo
            San Juan|Puerto Rico|18.4655|-66.1057|America/Puerto_Rico
            Bridgetown|Barbados|13.0975|-59.6167|America/Barbados
            Port of Spain|Trinidad and Tobago|10.6596|-61.5086|America/Port_of_Spain
            St. John's|Antigua and Barbuda|17.1274|-61.8468|America/Antigua
            Roseau|Dominica|15.3092|-61.3794|America/Dominica
            Castries|Saint Lucia|14.0101|-60.9875|America/St_Lucia
            Kingstown|Saint Vincent and the Grenadines|13.1600|-61.2248|America/St_Vincent
            St. George's|Grenada|12.0561|-61.7488|America/Grenada
            Basseterre|Saint Kitts and Nevis|17.3026|-62.7177|America/St_Kitts

            # South America
            Bogotá|Colombia|4.7110|-74.0721|America/Bogota
            Caracas|Venezuela|10.4806|-66.9036|America/Caracas
            Georgetown|Guyana|6.8013|-58.1551|America/Guyana
            Paramaribo|Suriname|5.8520|-55.2038|America/Paramaribo
            Cayenne|French Guiana|4.9224|-52.3135|America/Cayenne
            Quito|Ecuador|-0.1807|-78.4678|America/Guayaquil
            Guayaquil|Ecuador|-2.1709|-79.9224|America/Guayaquil
            Lima|Peru|-12.0464|-77.0428|America/Lima
            La Paz|Bolivia|-16.4897|-68.1193|America/La_Paz
            Santa Cruz de la Sierra|Bolivia|-17.7833|-63.1821|America/La_Paz
            Santiago|Chile|-33.4489|-70.6693|America/Santiago
            Valparaíso|Chile|-33.0472|-71.6127|America/Santiago
            Buenos Aires|Argentina|-34.6037|-58.3816|America/Argentina/Buenos_Aires
            Córdoba|Argentina|-31.4201|-64.1888|America/Argentina/Cordoba
            Montevideo|Uruguay|-34.9011|-56.1645|America/Montevideo
            Asunción|Paraguay|-25.2637|-57.5759|America/Asuncion
            São Paulo|Brazil|-23.5558|-46.6396|America/Sao_Paulo
            Rio de Janeiro|Brazil|-22.9068|-43.1729|America/Sao_Paulo
            Brasília|Brazil|-15.7939|-47.8828|America/Sao_Paulo
            Salvador|Brazil|-12.9777|-38.5016|America/Bahia
            Manaus|Brazil|-3.1190|-60.0217|America/Manaus
            Recife|Brazil|-8.0476|-34.8770|America/Recife
            Belém|Brazil|-1.4558|-48.4902|America/Belem
            Porto Alegre|Brazil|-30.0346|-51.2177|America/Sao_Paulo
            Punta Arenas|Chile|-53.1638|-70.9171|America/Punta_Arenas

            # Northern, Western, Southern, and Eastern Europe
            Reykjavík|Iceland|64.1466|-21.9426|Atlantic/Reykjavik
            Dublin|Ireland|53.3498|-6.2603|Europe/Dublin
            London|United Kingdom|51.5072|-0.1276|Europe/London
            Manchester|United Kingdom|53.4808|-2.2426|Europe/London
            Lisbon|Portugal|38.7223|-9.1393|Europe/Lisbon
            Porto|Portugal|41.1579|-8.6291|Europe/Lisbon
            Madrid|Spain|40.4168|-3.7038|Europe/Madrid
            Barcelona|Spain|41.3874|2.1686|Europe/Madrid
            Paris|France|48.8566|2.3522|Europe/Paris
            Marseille|France|43.2965|5.3698|Europe/Paris
            Brussels|Belgium|50.8503|4.3517|Europe/Brussels
            Amsterdam|Netherlands|52.3676|4.9041|Europe/Amsterdam
            Luxembourg|Luxembourg|49.6116|6.1319|Europe/Luxembourg
            Berlin|Germany|52.5200|13.4050|Europe/Berlin
            Hamburg|Germany|53.5511|9.9937|Europe/Berlin
            Munich|Germany|48.1351|11.5820|Europe/Berlin
            Bern|Switzerland|46.9480|7.4474|Europe/Zurich
            Zürich|Switzerland|47.3769|8.5417|Europe/Zurich
            Vienna|Austria|48.2082|16.3738|Europe/Vienna
            Prague|Czechia|50.0755|14.4378|Europe/Prague
            Bratislava|Slovakia|48.1486|17.1077|Europe/Bratislava
            Warsaw|Poland|52.2297|21.0122|Europe/Warsaw
            Kraków|Poland|50.0647|19.9450|Europe/Warsaw
            Budapest|Hungary|47.4979|19.0402|Europe/Budapest
            Ljubljana|Slovenia|46.0569|14.5058|Europe/Ljubljana
            Zagreb|Croatia|45.8150|15.9819|Europe/Zagreb
            Sarajevo|Bosnia and Herzegovina|43.8563|18.4131|Europe/Sarajevo
            Belgrade|Serbia|44.7866|20.4489|Europe/Belgrade
            Podgorica|Montenegro|42.4304|19.2594|Europe/Podgorica
            Skopje|North Macedonia|41.9981|21.4254|Europe/Skopje
            Tirana|Albania|41.3275|19.8187|Europe/Tirane
            Rome|Italy|41.9028|12.4964|Europe/Rome
            Milan|Italy|45.4642|9.1900|Europe/Rome
            Naples|Italy|40.8518|14.2681|Europe/Rome
            Valletta|Malta|35.8989|14.5146|Europe/Malta
            Athens|Greece|37.9838|23.7275|Europe/Athens
            Sofia|Bulgaria|42.6977|23.3219|Europe/Sofia
            Bucharest|Romania|44.4268|26.1025|Europe/Bucharest
            Chișinău|Moldova|47.0105|28.8638|Europe/Chisinau
            Kyiv|Ukraine|50.4501|30.5234|Europe/Kiev
            Minsk|Belarus|53.9006|27.5590|Europe/Minsk
            Vilnius|Lithuania|54.6872|25.2797|Europe/Vilnius
            Riga|Latvia|56.9496|24.1052|Europe/Riga
            Tallinn|Estonia|59.4370|24.7536|Europe/Tallinn
            Oslo|Norway|59.9139|10.7522|Europe/Oslo
            Stockholm|Sweden|59.3293|18.0686|Europe/Stockholm
            Helsinki|Finland|60.1699|24.9384|Europe/Helsinki
            Copenhagen|Denmark|55.6761|12.5683|Europe/Copenhagen
            Tórshavn|Faroe Islands|62.0079|-6.7900|Atlantic/Faroe
            Gibraltar|Gibraltar|36.1408|-5.3536|Europe/Gibraltar
            Andorra la Vella|Andorra|42.5063|1.5218|Europe/Andorra
            Monaco|Monaco|43.7384|7.4246|Europe/Monaco
            San Marino|San Marino|43.9424|12.4578|Europe/San_Marino
            Vatican City|Vatican City|41.9029|12.4534|Europe/Vatican

            # Russia, Caucasus, and Central Asia
            Moscow|Russia|55.7558|37.6173|Europe/Moscow
            Saint Petersburg|Russia|59.9311|30.3609|Europe/Moscow
            Yekaterinburg|Russia|56.8389|60.6057|Asia/Yekaterinburg
            Novosibirsk|Russia|55.0084|82.9357|Asia/Novosibirsk
            Vladivostok|Russia|43.1155|131.8855|Asia/Vladivostok
            Kaliningrad|Russia|54.7104|20.4522|Europe/Kaliningrad
            Tbilisi|Georgia|41.7151|44.8271|Asia/Tbilisi
            Yerevan|Armenia|40.1792|44.4991|Asia/Yerevan
            Baku|Azerbaijan|40.4093|49.8671|Asia/Baku
            Astana|Kazakhstan|51.1694|71.4491|Asia/Almaty
            Almaty|Kazakhstan|43.2220|76.8512|Asia/Almaty
            Bishkek|Kyrgyzstan|42.8746|74.5698|Asia/Bishkek
            Tashkent|Uzbekistan|41.2995|69.2401|Asia/Tashkent
            Dushanbe|Tajikistan|38.5598|68.7870|Asia/Dushanbe
            Ashgabat|Turkmenistan|37.9601|58.3261|Asia/Ashgabat

            # Middle East and North Africa
            Ankara|Türkiye|39.9334|32.8597|Europe/Istanbul
            Istanbul|Türkiye|41.0082|28.9784|Europe/Istanbul
            Nicosia|Cyprus|35.1856|33.3823|Asia/Nicosia
            Jerusalem|Israel|31.7683|35.2137|Asia/Jerusalem
            Tel Aviv|Israel|32.0853|34.7818|Asia/Jerusalem
            Amman|Jordan|31.9539|35.9106|Asia/Amman
            Beirut|Lebanon|33.8938|35.5018|Asia/Beirut
            Damascus|Syria|33.5138|36.2765|Asia/Damascus
            Baghdad|Iraq|33.3152|44.3661|Asia/Baghdad
            Tehran|Iran|35.6892|51.3890|Asia/Tehran
            Kuwait City|Kuwait|29.3759|47.9774|Asia/Kuwait
            Riyadh|Saudi Arabia|24.7136|46.6753|Asia/Riyadh
            Jeddah|Saudi Arabia|21.4858|39.1925|Asia/Riyadh
            Manama|Bahrain|26.2235|50.5876|Asia/Bahrain
            Doha|Qatar|25.2854|51.5310|Asia/Qatar
            Abu Dhabi|United Arab Emirates|24.4539|54.3773|Asia/Dubai
            Dubai|United Arab Emirates|25.2048|55.2708|Asia/Dubai
            Muscat|Oman|23.5880|58.3829|Asia/Muscat
            Sana'a|Yemen|15.3694|44.1910|Asia/Aden
            Cairo|Egypt|30.0444|31.2357|Africa/Cairo
            Alexandria|Egypt|31.2001|29.9187|Africa/Cairo
            Tripoli|Libya|32.8872|13.1913|Africa/Tripoli
            Tunis|Tunisia|36.8065|10.1815|Africa/Tunis
            Algiers|Algeria|36.7538|3.0588|Africa/Algiers
            Rabat|Morocco|34.0209|-6.8416|Africa/Casablanca
            Casablanca|Morocco|33.5731|-7.5898|Africa/Casablanca
            Nouakchott|Mauritania|18.0735|-15.9582|Africa/Nouakchott
            Khartoum|Sudan|15.5007|32.5599|Africa/Khartoum

            # Sub-Saharan Africa
            Praia|Cabo Verde|14.9331|-23.5133|Atlantic/Cape_Verde
            Dakar|Senegal|14.7167|-17.4677|Africa/Dakar
            Banjul|Gambia|13.4549|-16.5790|Africa/Banjul
            Bissau|Guinea-Bissau|11.8636|-15.5977|Africa/Bissau
            Conakry|Guinea|9.6412|-13.5784|Africa/Conakry
            Freetown|Sierra Leone|8.4657|-13.2317|Africa/Freetown
            Monrovia|Liberia|6.3156|-10.8074|Africa/Monrovia
            Bamako|Mali|12.6392|-8.0029|Africa/Bamako
            Ouagadougou|Burkina Faso|12.3714|-1.5197|Africa/Ouagadougou
            Niamey|Niger|13.5127|2.1128|Africa/Niamey
            Accra|Ghana|5.6037|-0.1870|Africa/Accra
            Lomé|Togo|6.1725|1.2314|Africa/Lome
            Porto-Novo|Benin|6.4969|2.6289|Africa/Porto-Novo
            Abuja|Nigeria|9.0765|7.3986|Africa/Lagos
            Lagos|Nigeria|6.5244|3.3792|Africa/Lagos
            Yaoundé|Cameroon|3.8480|11.5021|Africa/Douala
            Malabo|Equatorial Guinea|3.7504|8.7371|Africa/Malabo
            Libreville|Gabon|0.4162|9.4673|Africa/Libreville
            São Tomé|São Tomé and Príncipe|0.3365|6.7273|Africa/Sao_Tome
            Brazzaville|Republic of the Congo|-4.2634|15.2429|Africa/Brazzaville
            Kinshasa|Democratic Republic of the Congo|-4.4419|15.2663|Africa/Kinshasa
            Luanda|Angola|-8.8390|13.2894|Africa/Luanda
            Windhoek|Namibia|-22.5609|17.0658|Africa/Windhoek
            Gaborone|Botswana|-24.6282|25.9231|Africa/Gaborone
            Pretoria|South Africa|-25.7479|28.2293|Africa/Johannesburg
            Johannesburg|South Africa|-26.2041|28.0473|Africa/Johannesburg
            Cape Town|South Africa|-33.9249|18.4241|Africa/Johannesburg
            Maseru|Lesotho|-29.3151|27.4869|Africa/Maseru
            Mbabane|Eswatini|-26.3054|31.1367|Africa/Mbabane
            Maputo|Mozambique|-25.9692|32.5732|Africa/Maputo
            Harare|Zimbabwe|-17.8252|31.0335|Africa/Harare
            Lusaka|Zambia|-15.3875|28.3228|Africa/Lusaka
            Lilongwe|Malawi|-13.9626|33.7741|Africa/Blantyre
            Antananarivo|Madagascar|-18.8792|47.5079|Indian/Antananarivo
            Port Louis|Mauritius|-20.1609|57.5012|Indian/Mauritius
            Victoria|Seychelles|-4.6191|55.4513|Indian/Mahe
            Moroni|Comoros|-11.7172|43.2473|Indian/Comoro
            Dar es Salaam|Tanzania|-6.7924|39.2083|Africa/Dar_es_Salaam
            Dodoma|Tanzania|-6.1630|35.7516|Africa/Dar_es_Salaam
            Nairobi|Kenya|-1.2921|36.8219|Africa/Nairobi
            Kampala|Uganda|0.3476|32.5825|Africa/Kampala
            Kigali|Rwanda|-1.9441|30.0619|Africa/Kigali
            Bujumbura|Burundi|-3.3614|29.3599|Africa/Bujumbura
            Addis Ababa|Ethiopia|8.9806|38.7578|Africa/Addis_Ababa
            Djibouti|Djibouti|11.5721|43.1456|Africa/Djibouti
            Mogadishu|Somalia|2.0469|45.3182|Africa/Mogadishu
            Asmara|Eritrea|15.3229|38.9251|Africa/Asmara
            Juba|South Sudan|4.8594|31.5713|Africa/Juba
            Bangui|Central African Republic|4.3947|18.5582|Africa/Bangui
            N'Djamena|Chad|12.1348|15.0557|Africa/Ndjamena

            # South Asia
            Kabul|Afghanistan|34.5553|69.2075|Asia/Kabul
            Islamabad|Pakistan|33.6844|73.0479|Asia/Karachi
            Karachi|Pakistan|24.8607|67.0011|Asia/Karachi
            Lahore|Pakistan|31.5204|74.3587|Asia/Karachi
            New Delhi|India|28.6139|77.2090|Asia/Kolkata
            Mumbai|India|19.0760|72.8777|Asia/Kolkata
            Bengaluru|India|12.9716|77.5946|Asia/Kolkata
            Chennai|India|13.0827|80.2707|Asia/Kolkata
            Kolkata|India|22.5726|88.3639|Asia/Kolkata
            Hyderabad|India|17.3850|78.4867|Asia/Kolkata
            Dhaka|Bangladesh|23.8103|90.4125|Asia/Dhaka
            Kathmandu|Nepal|27.7172|85.3240|Asia/Kathmandu
            Thimphu|Bhutan|27.4728|89.6390|Asia/Thimphu
            Colombo|Sri Lanka|6.9271|79.8612|Asia/Colombo
            Malé|Maldives|4.1755|73.5093|Indian/Maldives

            # East Asia and Southeast Asia
            Beijing|China|39.9042|116.4074|Asia/Shanghai
            Shanghai|China|31.2304|121.4737|Asia/Shanghai
            Guangzhou|China|23.1291|113.2644|Asia/Shanghai
            Shenzhen|China|22.5431|114.0579|Asia/Shanghai
            Chengdu|China|30.5728|104.0668|Asia/Shanghai
            Chongqing|China|29.4316|106.9123|Asia/Shanghai
            Hong Kong|Hong Kong|22.3193|114.1694|Asia/Hong_Kong
            Macau|Macau|22.1987|113.5439|Asia/Macau
            Taipei|Taiwan|25.0330|121.5654|Asia/Taipei
            Ulaanbaatar|Mongolia|47.8864|106.9057|Asia/Ulaanbaatar
            Pyongyang|North Korea|39.0392|125.7625|Asia/Pyongyang
            Seoul|South Korea|37.5665|126.9780|Asia/Seoul
            Busan|South Korea|35.1796|129.0756|Asia/Seoul
            Tokyo|Japan|35.6762|139.6503|Asia/Tokyo
            Osaka|Japan|34.6937|135.5023|Asia/Tokyo
            Sapporo|Japan|43.0618|141.3545|Asia/Tokyo
            Hanoi|Vietnam|21.0278|105.8342|Asia/Ho_Chi_Minh
            Ho Chi Minh City|Vietnam|10.8231|106.6297|Asia/Ho_Chi_Minh
            Vientiane|Laos|17.9757|102.6331|Asia/Vientiane
            Phnom Penh|Cambodia|11.5564|104.9282|Asia/Phnom_Penh
            Bangkok|Thailand|13.7563|100.5018|Asia/Bangkok
            Yangon|Myanmar|16.8409|96.1735|Asia/Yangon
            Naypyidaw|Myanmar|19.7633|96.0785|Asia/Yangon
            Kuala Lumpur|Malaysia|3.1390|101.6869|Asia/Kuala_Lumpur
            Singapore|Singapore|1.3521|103.8198|Asia/Singapore
            Bandar Seri Begawan|Brunei|4.9031|114.9398|Asia/Brunei
            Jakarta|Indonesia|-6.2088|106.8456|Asia/Jakarta
            Surabaya|Indonesia|-7.2575|112.7521|Asia/Jakarta
            Denpasar|Indonesia|-8.6705|115.2126|Asia/Makassar
            Manila|Philippines|14.5995|120.9842|Asia/Manila
            Dili|Timor-Leste|-8.5569|125.5603|Asia/Dili

            # Oceania and the Pacific
            Perth|Australia|-31.9505|115.8605|Australia/Perth
            Adelaide|Australia|-34.9285|138.6007|Australia/Adelaide
            Melbourne|Australia|-37.8136|144.9631|Australia/Melbourne
            Canberra|Australia|-35.2809|149.1300|Australia/Sydney
            Sydney|Australia|-33.8688|151.2093|Australia/Sydney
            Brisbane|Australia|-27.4698|153.0251|Australia/Brisbane
            Darwin|Australia|-12.4634|130.8456|Australia/Darwin
            Hobart|Australia|-42.8821|147.3272|Australia/Hobart
            Auckland|New Zealand|-36.8509|174.7645|Pacific/Auckland
            Wellington|New Zealand|-41.2866|174.7756|Pacific/Auckland
            Christchurch|New Zealand|-43.5321|172.6362|Pacific/Auckland
            Port Moresby|Papua New Guinea|-9.4438|147.1803|Pacific/Port_Moresby
            Honiara|Solomon Islands|-9.4456|159.9729|Pacific/Guadalcanal
            Port Vila|Vanuatu|-17.7333|168.3273|Pacific/Efate
            Suva|Fiji|-18.1248|178.4501|Pacific/Fiji
            Nuku'alofa|Tonga|-21.1394|-175.2047|Pacific/Tongatapu
            Apia|Samoa|-13.8507|-171.7514|Pacific/Apia
            Funafuti|Tuvalu|-8.5211|179.1962|Pacific/Funafuti
            Tarawa|Kiribati|1.4518|172.9717|Pacific/Tarawa
            Majuro|Marshall Islands|7.0897|171.3803|Pacific/Majuro
            Palikir|Micronesia|6.9248|158.1610|Pacific/Pohnpei
            Ngerulmud|Palau|7.5004|134.6243|Pacific/Palau
            Yaren|Nauru|-0.5477|166.9209|Pacific/Nauru
            Nouméa|New Caledonia|-22.2758|166.4580|Pacific/Noumea
            Papeete|French Polynesia|-17.5516|-149.5585|Pacific/Tahiti
            Hagåtña|Guam|13.4443|144.7937|Pacific/Guam
            Saipan|Northern Mariana Islands|15.1778|145.7509|Pacific/Saipan
            Pago Pago|American Samoa|-14.2756|-170.7040|Pacific/Pago_Pago
            Avarua|Cook Islands|-21.2070|-159.7750|Pacific/Rarotonga
            Alofi|Niue|-19.0544|-169.8672|Pacific/Niue
            Mata-Utu|Wallis and Futuna|-13.2825|-176.1764|Pacific/Wallis
            Stanley|Falkland Islands|-51.6977|-57.8517|Atlantic/Stanley
            Jamestown|Saint Helena|-15.9387|-5.7168|Atlantic/St_Helena
            Longyearbyen|Svalbard and Jan Mayen|78.2232|15.6469|Arctic/Longyearbyen
            """;
}
