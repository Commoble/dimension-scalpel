package commoble.dimensionscalpel;

import commoble.dimensionscalpel.ConfigHelper.ConfigValueListener;
import net.minecraftforge.common.ForgeConfigSpec;

public class ServerConfig
{
	public final ConfigValueListener<Integer> removePermission;
	public final ConfigValueListener<Integer> resetPermission;
	public final ConfigValueListener<Integer> printPermission;
	
	public ServerConfig(ForgeConfigSpec.Builder builder, ConfigHelper.Subscriber subscriber)
	{
		builder.push("commands");
		
		this.removePermission = subscriber.subscribe(builder
			.comment("Permission level required to use /dimensionscalpel remove and remove_all")
			.define("remove_permission", 4));
		
		this.resetPermission = subscriber.subscribe(builder
			.comment("Permission level required to use /dimensionscalpel reset_dimension_registry")
			.define("reset_permission", 4));
		
		this.printPermission = subscriber.subscribe(builder
			.comment("Permission level required to use /dimensionscalpel print")
			.define("remove_permission", 2));
		
		builder.pop();
	}
}
