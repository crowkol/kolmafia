/**
 * Copyright (c) 2005, KoLmafia development team
 * http://kolmafia.sourceforge.net/
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *  [1] Redistributions of source code must retain the above copyright
 *      notice, this list of conditions and the following disclaimer.
 *  [2] Redistributions in binary form must reproduce the above copyright
 *      notice, this list of conditions and the following disclaimer in
 *      the documentation and/or other materials provided with the
 *      distribution.
 *  [3] Neither the name "KoLmafia development team" nor the names of
 *      its contributors may be used to endorse or promote products
 *      derived from this software without specific prior written
 *      permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package net.sourceforge.kolmafia;
import java.util.List;

/**
 * An extension of <code>KoLRequest</code> designed to handle all the
 * item creation requests.  At the current time, it is only made to
 * handle items which use meat paste and are tradeable in-game.
 */

public class ItemCreationRequest extends KoLRequest implements Comparable
{
	public static final int MEAT_PASTE = 25;
	public static final int MEAT_STACK = 88;
	public static final int DENSE_STACK = 258;

	public static final int NOCREATE = 0;
	public static final int COMBINE = 1;
	public static final int COOK = 2;
	public static final int MIX = 3;
	public static final int SMITH = 4;
	public static final int COOK_REAGENT = 5;
	public static final int COOK_PASTA = 6;

	private int itemID, quantityNeeded, mixingMethod;

	/**
	 * Constructs a new <code>ItemCreationRequest</code> where you create
	 * the given number of items.
	 *
	 * @param	client	The client to be notified of the item creation
	 * @param	itemID	The identifier for the item to be created
	 * @param	mixingMethod	How the item is created
	 * @param	quantityNeeded	How many of this item are needed
	 */

	public ItemCreationRequest( KoLmafia client, int itemID, int mixingMethod, int quantityNeeded )
	{
		super( client, mixingMethod == COMBINE ? "combine.php" : mixingMethod == MIX ? "cocktail.php" :
			(mixingMethod == COOK || mixingMethod == COOK_REAGENT || mixingMethod == COOK_PASTA) ? "cook.php" :
			mixingMethod == SMITH ? "smith.php" : "" );

		addFormField( "action", "combine" );
		addFormField( "pwd", client.getPasswordHash() );

		this.itemID = itemID;
		this.mixingMethod = mixingMethod;
		this.quantityNeeded = quantityNeeded;
	}

	/**
	 * Compares the <code>AdventureResult</code> with the given object for name
	 * equality and priority differences.  Return values are consistent with the
	 * rules laid out in {@link java.lang.Comparable#compareTo(Object)}.
	 */

	public int compareTo( Object o )
	{
		if ( !(o instanceof ItemCreationRequest) || o == null )
			return -1;

		ItemCreationRequest icr = (ItemCreationRequest) o;
		return TradeableItemDatabase.getItemName( itemID ).compareTo( TradeableItemDatabase.getItemName( icr.itemID ) );
	}

	/**
	 * Runs the item creation request.  Note that if another item needs
	 * to be created for the request to succeed, this method will fail.
	 */

	public void run()
	{
		switch ( itemID )
		{

			// Requests for meat paste are handled separately; the
			// full request is broken into increments of 1000, 100
			// and 10 and then submitted to the server.

			case MEAT_PASTE:
			{
				while ( quantityNeeded > 1000 )
				{
					(new MeatPasteRequest( client, 1000 )).run();
					quantityNeeded -= 1000;
				}
				while ( quantityNeeded > 100 )
				{
					(new MeatPasteRequest( client, 100 )).run();
					quantityNeeded -= 100;
				}
				while ( quantityNeeded > 10 )
				{
					(new MeatPasteRequest( client, 10 )).run();
					quantityNeeded -= 10;
				}
				for ( int i = 0; i < quantityNeeded; ++i )
					(new MeatPasteRequest( client, 1 )).run();
				break;
			}

			// Requests for meat stacks are handled separately; the
			// full request must be done one at a time (there is no
			// way to make more than 1 meat stack at a time in KoL).

			case MEAT_STACK:
			case DENSE_STACK:
			{
				boolean isDense = (itemID == DENSE_STACK);
				for ( int i = 0; i < quantityNeeded; ++i )
					(new MeatStackRequest( client, isDense )).run();
				break;
			}

			// In order to make indentation cleaner, an internal class
			// a secondary method is called to handle standard item
			// creation requests.  Note that smithing is not currently
			// handled because I don't have a SC and have no idea
			// which page is requested for smithing.

			default:

				switch ( mixingMethod )
				{
					case COMBINE:
					case COOK:
					case MIX:
					case SMITH:
					case COOK_REAGENT:
					case COOK_PASTA:
						combineItems();
						break;
				}

				break;
		}
	}

	/**
	 * Helper routine which actually does the item combination.
	 */

	private void combineItems()
	{
		int [][] ingredients = ConcoctionsDatabase.getIngredients( itemID );

		if ( ingredients != null )
		{
			makeIngredient( ingredients[0][0], ingredients[0][1] );
			if ( ingredients[0][0] != ingredients[1][0] )
	 			makeIngredient( ingredients[1][0], ingredients[1][1] );
		}

		// Check to see if you need meat paste in order
		// to create the needed quantity of items, and
		// create any needed meat paste.

		if ( mixingMethod == COMBINE )
			makeIngredient( MEAT_PASTE, COMBINE );

		// Now that the item's been created, you can
		// actually do the request!

		addFormField( "item1", "" + ingredients[0][0] );
		addFormField( "item2", "" + ingredients[1][0] );
		addFormField( "quantity", "" + quantityNeeded );

		super.run();

		client.addToResultTally( new AdventureResult( TradeableItemDatabase.getItemName( ingredients[0][0] ), 0 - quantityNeeded ) );
		client.addToResultTally( new AdventureResult( TradeableItemDatabase.getItemName( ingredients[1][0] ), 0 - quantityNeeded ) );

		if ( mixingMethod == COMBINE )
			client.addToResultTally( new AdventureResult( "meat paste", 0 - quantityNeeded ) );

		client.addToResultTally( new AdventureResult( TradeableItemDatabase.getItemName( itemID ), quantityNeeded ) );
	}

	/**
	 * Helper routine which makes more of the given ingredient, if it
	 * is needed.
	 *
	 * @param	ingredientID	The ingredient to make
	 * @param	mixingMethod	How the ingredient is prepared
	 */

	private void makeIngredient( int ingredientID, int mixingMethod )
	{
		List inventory = client.getInventory();
		int index = inventory.indexOf( new AdventureResult( TradeableItemDatabase.getItemName( ingredientID ), 0 ) );
		int currentQuantity = (index == -1) ? 0 : ((AdventureResult)inventory.get( index )).getCount();

		int actualQuantityNeeded = quantityNeeded - currentQuantity;

		// In order to minimize server overload by making exact quantities,
		// the client will attempt to overcompensate by making more meat
		// paste than is necessary.

		if ( ingredientID == MEAT_PASTE )
			actualQuantityNeeded =
				( actualQuantityNeeded > 1000 ) ? ((int) Math.ceil( actualQuantityNeeded / 1000 )) * 1000 :
				( actualQuantityNeeded > 100 ) ? 1000 :
				( actualQuantityNeeded > 10 ) ? 100 :
				( actualQuantityNeeded > 1 ) ? 10 : 1;

		if ( currentQuantity < quantityNeeded )
			(new ItemCreationRequest( client, ingredientID, mixingMethod, actualQuantityNeeded )).run();
	}

	/**
	 * Returns the string form of this item creation request.
	 * This displays the item name, and the amount that will
	 * be created by this request.
	 *
	 * @return	The string form of this request
	 */

	public String toString()
	{	return TradeableItemDatabase.getItemName( itemID ) + " (" + quantityNeeded + ")";
	}

	/**
	 * An internal class made to create meat paste.  This class
	 * takes only values of 10, 100, or 1000; it is the job of
	 * other classes to break up the request to create as much
	 * meat paste as is desired.
	 */

	private class MeatPasteRequest extends KoLRequest
	{
		private int quantityNeeded;

		public MeatPasteRequest( KoLmafia client, int quantityNeeded )
		{
			super( client, "inventory.php" );
			addFormField( "which", "3" );
			addFormField( "action", ((quantityNeeded == 1) ? "meat" : "" + quantityNeeded) + "paste" );

			this.quantityNeeded = quantityNeeded;
		}

		public void run()
		{
			super.run();
			client.addToResultTally( new AdventureResult( AdventureResult.MEAT, -10 * quantityNeeded ) );
			client.addToResultTally( new AdventureResult( "meat paste", quantityNeeded ) );
		}
	}

	/**
	 * An internal class made to create meat stacks and dense
	 * meat stacks.  Note that this only creates one meat stack
	 * of the type desired; it is the job of other classes to
	 * break up the request to create as many meat stacks as is
	 * actually desired.
	 */

	private class MeatStackRequest extends KoLRequest
	{
		private boolean isDense;

		public MeatStackRequest( KoLmafia client, boolean isDense )
		{
			super( client, "inventory.php" );
			addFormField( "which", "3" );
			addFormField( "action", isDense ? "densestack" : "meatstack" );
			this.isDense = isDense;
		}

		public void run()
		{
			super.run();
			client.addToResultTally( new AdventureResult( AdventureResult.MEAT, (isDense ? -1000 : -100) * quantityNeeded ) );
			client.addToResultTally( new AdventureResult( (isDense ? "dense " : "") + "meat stack", 1 ) );
		}
	}
}