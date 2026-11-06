package fr.nylerp.resolveur;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Résolveur de noms du JEU (passé en -Xbootclasspath/a par NyleLauncher, chargé par ServiceLoader — JEP 418).
 *
 * Sur macOS, InetAddress.getLocalHost() demande au système l'adresse du NOM DE LA MACHINE (« Mon-Mac.local ») ; quand
 * mDNS ne répond pas pour ce nom, getaddrinfo échoue au bout de 5 s exactement, puis Java se rabat sur les interfaces
 * réseau. log4j fait cet appel avant même que Fabric écrive sa première ligne : 5 s perdues à CHAQUE lancement
 * (mesuré le 06/11/2026 : 5,01 s ; getaddrinfo AF_INET, AF_INET6 et AF_UNSPEC : 5,0 s chacun).
 *
 * Ce résolveur ne change QUE ce cas : le nom de la machine (avec ou sans « .local ») reçoit aussitôt les adresses des
 * interfaces actives (ce que Java finit par rendre de toute façon), puis le bouclage. Tout autre nom passe par le résolveur
 * intégré de la JVM, inchangé.
 */
public final class ResolveurLocal extends InetAddressResolverProvider {

    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver integre = configuration.builtinResolver();
        String local = configuration.lookupLocalHostName();
        return new InetAddressResolver() {
            @Override
            public Stream<InetAddress> lookupByName(String host, LookupPolicy policy) throws UnknownHostException {
                if (estLocal(host, local)) {
                    List<InetAddress> a = adressesLocales(host, policy);
                    if (!a.isEmpty()) return a.stream();
                }
                return integre.lookupByName(host, policy);
            }

            @Override
            public String lookupByAddress(byte[] addr) throws UnknownHostException {
                return integre.lookupByAddress(addr);
            }
        };
    }

    static boolean estLocal(String host, String local) {
        if (host == null || local == null || local.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT), l = local.toLowerCase(Locale.ROOT);
        String sans = l.endsWith(".local") ? l.substring(0, l.length() - 6) : l;
        return h.equals(l) || h.equals(sans) || h.equals(sans + ".local");
    }

    static List<InetAddress> adressesLocales(String host, InetAddressResolver.LookupPolicy policy) {
        boolean v4 = (policy.characteristics() & InetAddressResolver.LookupPolicy.IPV4) != 0;
        boolean v6 = (policy.characteristics() & InetAddressResolver.LookupPolicy.IPV6) != 0;
        List<InetAddress> r = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (a.isLinkLocalAddress()) continue;
                    if (a instanceof Inet4Address ? v4 : v6) r.add(InetAddress.getByAddress(host, a.getAddress()));
                }
            }
            if (v4) r.add(InetAddress.getByAddress(host, new byte[]{127, 0, 0, 1}));
        } catch (Exception e) {
            return List.of();   // en cas de doute : le résolveur intégré, comme avant
        }
        // IPv4 d'abord (même ordre que le repli de la JVM sur macOS)
        r.sort((x, y) -> Boolean.compare(!(x instanceof Inet4Address), !(y instanceof Inet4Address)));
        return r;
    }

    @Override
    public String name() {
        return "nyle-resolveur-local";
    }
}
