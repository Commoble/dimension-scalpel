package commoble.dimensionscalpel;

import commoble.dimensionscalpel.ConfigHelper.ConfigValueListener;
import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig
{
	public final ConfigValueListener<Integer> removePermission;
	
	public ServerConfig(ForgeConfigSpec.Builder builder, ConfigHelper.Subscriber subscriber)
	{
		builder.push("commands");
		
		this.removePermission = subscriber.subscribe(builder
			.comment("Permission level required to use /dimensionscalpel remove")
			.define("remove_permission", 4));
		
		builder.pop();
	}
}
