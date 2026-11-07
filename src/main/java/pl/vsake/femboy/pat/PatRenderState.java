package pl.vsake.femboy.pat;

/** Dokładane mixinem do LivingEntityRenderState: czas animacji pat pat (klatki GIF-a), -1 = nikt nie głaszcze. */
public interface PatRenderState {
	float femboy$getPatTime();

	void femboy$setPatTime(float time);
}
