package fr.bountycraft.util;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/** Effets visuels et petites aides géométriques. */
public final class Fx {
    private Fx() {}

    /**
     * Fait apparaître une particule en gérant automatiquement les particules qui exigent
     * une donnée (ex. DRAGON_BREATH dans les versions récentes).
     */
    public static void particle(World w, Particle p, Location l, int count,
                                double dx, double dy, double dz, double extra) {
        Class<?> type = p.getDataType();
        if (type == Void.class) {
            w.spawnParticle(p, l, count, dx, dy, dz, extra);
        } else if (type == Float.class) {
            w.spawnParticle(p, l, count, dx, dy, dz, extra, 1.0f);
        }
    }

    public static void ring(Location center, double radius, int points, Particle p) {
        World w = center.getWorld();
        for (int i = 0; i < points; i++) {
            double a = 2 * Math.PI * i / points;
            Location l = center.clone().add(Math.cos(a) * radius, 0, Math.sin(a) * radius);
            particle(w, p, l, 1, 0, 0, 0, 0);
        }
    }

    /** Descend depuis un point jusqu'au sol (max {@code maxDown} blocs). */
    public static Location ground(Location from, double maxDown) {
        World w = from.getWorld();
        RayTraceResult r = w.rayTraceBlocks(from, new Vector(0, -1, 0), maxDown,
                FluidCollisionMode.NEVER, true);
        if (r == null) return from;
        Vector h = r.getHitPosition();
        return new Location(w, h.getX(), h.getY() + 0.05, h.getZ());
    }
}
