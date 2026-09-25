package ru.dez.client.camera;

import org.joml.Vector2d;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RTSClientState {

    public static boolean hasVillagers = false;
    /** Town hall tier known on the client (0 = none, 1..3). */
    public static int townHallLevel = 0;
    public static boolean placementMode = false;
    public static boolean professionPanelOpen = false;

    public static int wood = 0;
    public static int food = 0;
    public static int ore = 0;
    public static int happiness = 100;

    public static final Set<UUID> selectedVillagers = new LinkedHashSet<>();
    public static final Set<UUID> phantomIds = new LinkedHashSet<>();

    public static final List<Rect> unitButtons = new ArrayList<>();
    public static final List<UUID> unitIds = new ArrayList<>();

    public static final List<Rect> profButtons = new ArrayList<>();
    public static final List<String> profActions = new ArrayList<>();

    public static Rect villagerButton = new Rect(0, 0, 0, 0);
    public static Rect professionButton = new Rect(0, 0, 0, 0);

    /** Town-hall hire panel (opened by clicking a town hall in the world). */
    public static boolean townHallPanelOpen = false;
    public static int hireQueue = 0;
    public static int hireCost = 12;
    public static final List<Rect> townHallButtons = new ArrayList<>();
    public static final List<String> townHallActions = new ArrayList<>();

    /** Team/invite panel opened by the "+" button in the top-right corner. */
    public static boolean teamMenuOpen = false;
    public static Rect teamButton = new Rect(0, 0, 0, 0);
    public static final List<Rect> teamButtons = new ArrayList<>();
    public static final List<String> teamActions = new ArrayList<>();

    public static boolean selecting = false;
    public static Vector2d selectionStart = null;

    public static void resetSelection() {
        selecting = false;
        selectionStart = null;
        selectedVillagers.clear();
        unitButtons.clear();
        unitIds.clear();
    }

    public static void resetAll() {
        hasVillagers = false;
        placementMode = false;
        professionPanelOpen = false;

        wood = 0;
        food = 0;
        ore = 0;
        happiness = 100;

        selectedVillagers.clear();
        phantomIds.clear();
        unitButtons.clear();
        unitIds.clear();
        profButtons.clear();
        profActions.clear();
        teamButtons.clear();
        teamActions.clear();
        townHallButtons.clear();
        townHallActions.clear();

        teamMenuOpen = false;
        townHallPanelOpen = false;
        hireQueue = 0;

        villagerButton = new Rect(0, 0, 0, 0);
        professionButton = new Rect(0, 0, 0, 0);
        teamButton = new Rect(0, 0, 0, 0);

        selecting = false;
        selectionStart = null;
    }

    public static class Rect {
        public int x;
        public int y;
        public int w;
        public int h;

        public Rect(int x, int y, int w, int h) {
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public boolean contains(double px, double py) {
            return px >= x && px <= x + w && py >= y && py <= y + h;
        }
    }
}