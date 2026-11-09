package bayern.kickner.buchstabierer

enum class Table(val short: String, val nameGer: String, val nameGer2022: String, val international: String) {

    A("A", "Anton", "Aachen", "Alfa"),
    AE("Ä", "Ärger", "Umlaut Aachen", "Ä"),
    B("B", "Berta", "Berlin", "Bravo"),
    C("C", "Cäsar", "Chemnitz", "Charlie"),
    CH("Ch", "Charlotte", "Chemnitz Hamburg", "Ch"),
    D("D", "Dora", "Düsseldorf", "Delta"),
    E("E", "Emil", "Essen", "Echo"),
    F("F", "Friedrich", "Frankfurt", "Foxtrot"),
    G("G", "Gustav", "Goslar", "Golf"),
    H("H", "Heinrich", "Hamburg", "Hotel"),
    I("I", "Ida", "Ingelheim", "India"),
    J("J", "Julius", "Jena", "Juliett"),
    K("K", "Kaufmann", "Köln", "Kilo"),
    L("L", "Ludwig", "Leipzig", "Lima"),
    M("M", "Martha", "München", "Mike"),
    N("N", "Nordpol", "Nürnberg", "November"),
    O("O", "Otto", "Offenbach", "Oscar"),
    OE("Ö", "Ökonom", "Umlaut Offenbach", "Ö"),
    P("P", "Paula", "Potsdam", "Papa"),
    Q("Q", "Quelle", "Quickborn", "Quebec"),
    R("R", "Richard", "Rostock", "Romeo"),
    S("S", "Samuel", "Salzwedel", "Sierra"),
    SCH("Sch", "Schule", "Salzwedel Chemnitz Hamburg", "Sch"),
    SS("ẞ", "Eszett", "Eszett", "ss"),
    T("T", "Theodor", "Tübingen", "Tango"),
    U("U", "Ulrich", "Unna", "Uniform"),
    UE("Ü", "Übermut", "Umlaut Unna", "Ü"),
    V("V", "Viktor", "Völklingen", "Victor"),
    W("W", "Wilhelm", "Wuppertal", "Whiskey"),
    X("X", "Xanthippe", "Xanten", "X-Ray"),
    Y("Y", "Ypsilon", "Ypsilon", "Yankee"),
    Z("Z", "Zacharias", "Zwickau", "Zulu");

}