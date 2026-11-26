package dev.oculifelis.careerbees.effect;

/** Registered so species data resolves; the behaviour lands with the block or item it needs. */
public final class IdleEffect extends CareerEffect {
    public IdleEffect(String id) {
        super(id, 0, 1);
    }

    @Override
    protected void fire(Ctx ctx) {
    }
}
